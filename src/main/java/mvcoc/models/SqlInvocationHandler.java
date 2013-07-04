package mvcoc.models;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.cache.annotation.CachePut;
import javax.cache.annotation.CacheRemoveEntry;
import javax.cache.annotation.CacheResult;

import mvcoc.util.ClassUtils;
import mvcoc.util.ConcurrentLinkedHashMap;

import org.apache.tomcat.dbcp.dbcp.BasicDataSource;

public class SqlInvocationHandler implements InvocationHandler {

	private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([_.0-9A-Za-z]+)\\}");

	private static final Pattern COMMA_PATTERN = Pattern.compile("\\,");

	private final Properties sqls;

	private final Properties cacheConfigs;
	
	private final BasicDataSource datasource;
	
	private final ConcurrentMap<String, Map<String, Object>> caches = new ConcurrentHashMap<String, Map<String,Object>>();
	
	public SqlInvocationHandler(BasicDataSource datasource, Properties sqls, Properties cacheConfigs) {
		this.datasource = datasource;
		this.sqls = sqls;
		this.cacheConfigs = cacheConfigs;
	}
	
	public void validate(Object proxy, Method method, Object[] args) throws Throwable {
		method.getParameterTypes();
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {
		validate(proxy, method, args);
		CacheResult cacheResult = method.getAnnotation(CacheResult.class);
		if (cacheResult != null) {
			Map<String, Object> cache = getCache(cacheResult.cacheName());
			String key = getCacheKey(args);
			Object result = cache.get(key);
			if (result != null) {
				return result;
			}
			result = invokeSql(proxy, method, args);
			if (result != null) {
				cache.put(key, result);
			}
			return result;
		}
		Object result = invokeSql(proxy, method, args);
		CacheRemoveEntry cacheRemoveEntry = method.getAnnotation(CacheRemoveEntry.class);
		if (cacheRemoveEntry != null) {
			String cacheNames = cacheRemoveEntry.cacheName();
			for (String cacheName : COMMA_PATTERN.split(cacheNames)) {
				Map<String, Object> cache = getCache(cacheName);
				String key = getCacheKey(args);
				cache.remove(key);
			}
		} else {
			CachePut cachePut = method.getAnnotation(CachePut.class);
			if (cachePut != null) {
				String cacheNames = cachePut.cacheName();
				for (String cacheName : COMMA_PATTERN.split(cacheNames)) {
					Map<String, Object> cache = getCache(cacheName);
					String key = getCacheKey(args);
					cache.remove(key);
				}
			}
		}
		return result;
	}
	
	private String getCacheKey(Object[] args) {
		if (args == null || args.length == 0) {
			return "";
		}
		Object value = args[0];
		if (value == null) {
			return "";
		}
		if (value instanceof String) {
			return (String) value;
		}
		if (value instanceof Number) {
			return String.valueOf(value);
		}
		try {
			return String.valueOf(value.getClass().getMethod("getName", new Class<?>[0]).invoke(value, new Object[0]));
		} catch (Exception e) {
			return String.valueOf(value);
		}
	}
	
	private Map<String, Object> getCache(String cacheName) {
		Map<String, Object> cache = caches.get(cacheName);
		if (cache == null) {
			int capacity = Integer.parseInt(cacheConfigs.getProperty(cacheName + ".capacity", "1000").trim());
			cache = new ConcurrentLinkedHashMap<String, Object>(capacity);
			Map<String, Object> old = caches.putIfAbsent(cacheName, cache);
			if (old != null) {
				cache = old;
			}
		}
		return cache;
	}

	@SuppressWarnings("unchecked")
	public Object invokeSql(Object proxy, Method method, Object[] args)
			throws Throwable {
		Object arg = args == null || args.length == 0 ? null : args[0];
		String sql = sqls.getProperty(method.getName());
		StringBuffer buf = new StringBuffer();
		List<Object> params = new ArrayList<Object>();
        Matcher matcher = VAR_PATTERN.matcher(sql);
		while(matcher.find()) {
			String var = matcher.group(1);
            matcher.appendReplacement(buf, "?");
            if (arg == null) {
            	params.add(null);
            } else if (arg instanceof String || arg instanceof Number || arg.getClass().isPrimitive()) {
            	params.add(arg);
    		} else {
    			params.add(ClassUtils.getProperty(arg, var));
    		}
        }
        matcher.appendTail(buf);
        sql = buf.toString().trim();
		try {
            Connection conn = datasource.getConnection();
            try {
	            PreparedStatement stmt = conn.prepareStatement(sql);
            	if (params.size() > 0) {
            		for (int i = 0, n = params.size(); i < n; i ++) {
            			stmt.setObject(i + 1, params.get(i));
            		}
            	}
                try {
                	if (sql.startsWith("select")) {
	                    ResultSet rs = stmt.executeQuery();
	                    try {
	                        ResultSetMetaData rsmd = rs.getMetaData();
	                        List<Object> list = null;
	                        while (rs.next()) {
	                            if (list == null)
	                                list = new ArrayList<Object>();
	                            int columnCount = rsmd.getColumnCount();
	                        	if (columnCount == 1) {
	                        	    list.add(rs.getObject(1));
	                        	} else {
	                        		Map<String, Object> map = new HashMap<String, Object>();
	                                for (int i = 1; i <= columnCount; i++) {
	                                	String name = rsmd.getColumnLabel(i);
	                                	if (name == null)
	                                		name = rsmd.getColumnName(i);
	                                    map.put(name, rs.getObject(i));
	                                }
	                                list.add(map);
	                        	}
	                        }
	                        if (list == null || list.size() == 0) {
	                        	return null;
	                        }
	                        if (method.getReturnType().isAssignableFrom(ArrayList.class)) {
	                        	return list;
	                        } else if (method.getReturnType().isArray()) {
	                        	Class<?> beanClass = method.getReturnType().getComponentType();
	                        	Object array = Array.newInstance(beanClass, list.size());
	                        	for (int i = 0; i < list.size(); i ++) {
	                        		Object value = list.get(i);
	                        		if (value instanceof Map) {
	                        			value = ClassUtils.convertBean((Map<String, Object>) value, beanClass);
	                        		}
	                        		Array.set(array, i, value);
	                        	}
	                        	return array;
	                        } else {
	                        	Object value = list.get(0);
                        		if (value instanceof Map) {
                        			value = ClassUtils.convertBean((Map<String, Object>) value, method.getReturnType());
                        		}
	                        	return ClassUtils.convertValue(value, method.getReturnType());
	                        }
	                    } finally {
	                        rs.close();
	                    }
                	} else {
                		int row = stmt.executeUpdate();
                		if (boolean.class.equals(method.getReturnType())
                				|| method.getReturnType().isAssignableFrom(Boolean.class)) {
                			return row > 0;
                		} else if (int.class.equals(method.getReturnType())
                				|| method.getReturnType().isAssignableFrom(Integer.class)) {
                			return row;
                		}
                		return null;
					}
                } finally {
                    stmt.close();
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) {
        	System.err.println("Failed to execute sql "+ sql + " with " + params);
            e.printStackTrace();
            if (boolean.class.equals(method.getReturnType())
    				|| Boolean.class.equals(method.getReturnType())) {
    			return false;
    		} else if (char.class.equals(method.getReturnType())
    				|| Character.class.equals(method.getReturnType())) {
    			return '\0';
    		} else if (method.getReturnType().isPrimitive()
    				|| Number.class.isAssignableFrom(method.getReturnType())) {
    			return 0;
    		}
            return null;
        }
	}

}
