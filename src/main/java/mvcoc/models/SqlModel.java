package mvcoc.models;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import mvcoc.Model;
import mvcoc.util.ClassUtils;
import mvcoc.util.PropertiesUtils;

import org.apache.tomcat.dbcp.dbcp.BasicDataSource;

public class SqlModel implements Model {
    
    public static final String INITIAL_SIZE_KEY = "initialSize";
    
    public static final String MAX_ACTIVE_KEY = "maxActive";
    
    public static final String MAX_IDLE_KEY = "maxIdle";
    
    public static final String MIN_IDLE_KEY = "minIdle";
    
    public static final String MAX_WAIT_KEY = "maxWait";
    
    public static final String TIME_BETWEEN_EVICTION_RUNS_MILLIS_KEY = "timeBetweenEvictionRunsMillis";
    
    public static final String NUM_TESTS_PER_EVICTION_RUN_KEY = "numTestsPerEvictionRun";
    
    public static final String MIN_EVICTABLE_IDLE_TIME_MILLIS_KEY = "minEvictableIdleTimeMillis";
    
    public static final String VALIDATION_QUERY_KEY = "validationQuery";

    public static final String TEST_ON_BORROW_KEY = "testOnBorrow";
    
    public static final String TEST_WHILE_IDLE_KEY = "testWhileIdle";
    
    public static final String TEST_ON_RETURN_KEY = "testOnReturn";

	private final Map<String, Object> modelInstances = new ConcurrentHashMap<String, Object>();

	private String modelExtension;

	private String cacheExtension;

	private String modelPackage;

	private String modelDirectory;

	private String datasourceDriver;

	private String datasourceUrl;

	private String datasourceUsername;

	private String datasourcePassword;

    private BasicDataSource datasource = new BasicDataSource();

	public void setProperties(Properties properties) {
		modelExtension = properties.getProperty("model.extension");
		cacheExtension = properties.getProperty("cache.extension");
	    modelPackage = properties.getProperty("model.package");
	    modelDirectory = properties.getProperty("model.directory");
		datasourceUrl = properties.getProperty("model.datasource.url");
	    datasourceUsername = properties.getProperty("model.datasource.username");
	    datasourcePassword = properties.getProperty("model.datasource.password");
	    datasourceDriver = properties.getProperty("model.datasource.driver");
        
        datasource.setDriverClassName(datasourceDriver);
        datasource.setUrl(datasourceUrl);
        datasource.setUsername(datasourceUsername);
        datasource.setPassword(datasourcePassword);
        datasource.setInitialSize(PropertiesUtils.getInt(properties, INITIAL_SIZE_KEY, 5)); // 初始连接数
        datasource.setMaxActive(PropertiesUtils.getInt(properties, MAX_ACTIVE_KEY, 30)); // 最大连接数，JSPKJ最多只允许30个连接
        datasource.setMaxIdle(PropertiesUtils.getInt(properties, MAX_IDLE_KEY, 5)); // 最大空闲连接数
        datasource.setMinIdle(PropertiesUtils.getInt(properties, MIN_IDLE_KEY, 0)); // 最小空闲连接数
        datasource.setMaxWait(PropertiesUtils.getInt(properties, MAX_WAIT_KEY, 1000 * 5)); // 连接不够时，最大等待时间
        datasource.setTimeBetweenEvictionRunsMillis(PropertiesUtils.getInt(properties, TIME_BETWEEN_EVICTION_RUNS_MILLIS_KEY, 1000 * 60)); // 空闲连接扫描时间
        datasource.setNumTestsPerEvictionRun(PropertiesUtils.getInt(properties, NUM_TESTS_PER_EVICTION_RUN_KEY, 5)); // 每次扫描连接数
        datasource.setMinEvictableIdleTimeMillis(PropertiesUtils.getInt(properties, MIN_EVICTABLE_IDLE_TIME_MILLIS_KEY, 1000 * 60 * 60 * 8)); // 连接最大空闲时间，MYSQL缺省timeout为8小时
        datasource.setValidationQuery(properties.getProperty(VALIDATION_QUERY_KEY, "select 1")); // 测试连接是否有效的SQL
        datasource.setTestOnBorrow(PropertiesUtils.getBoolean(properties, TEST_ON_BORROW_KEY, false)); // 当获取连接时，测试其有效性
        datasource.setTestWhileIdle(PropertiesUtils.getBoolean(properties, TEST_WHILE_IDLE_KEY, true)); // 当连接空闲时，测试其有效性
        datasource.setTestOnReturn(PropertiesUtils.getBoolean(properties, TEST_ON_RETURN_KEY, false)); // 当连接返回连接池时，测试其有效性
	}
	
	public <T> T get(Class<T> type, String name) {
		return get(name);
	}
	
	public <T> T get(Class<T> type) {
		return get(type.getSimpleName());
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String modelName) {
		try {
			modelName = modelName.substring(0, 1).toUpperCase() + modelName.substring(1);
			Object model = modelInstances.get(modelName);
			if (model == null) {
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
				Class<?> modelClass = ClassUtils.forName(modelPackage + "." + modelName);
				Properties sqls = new Properties();
				sqls.load(classLoader.getResourceAsStream(modelDirectory + "/" + modelName + modelExtension));
				Properties caches = new Properties();
				InputStream in = classLoader.getResourceAsStream(modelDirectory + "/" + modelName + cacheExtension);
				if (in != null) {
					caches.load(in);
				}
				model = Proxy.newProxyInstance(classLoader, new Class<?>[] {modelClass}, new SqlInvocationHandler(datasource, sqls, caches));
				modelInstances.put(modelName, model);
			}
			return (T) model;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

}
