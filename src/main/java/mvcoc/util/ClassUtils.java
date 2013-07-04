package mvcoc.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class ClassUtils {

	@SuppressWarnings("unchecked")
	public static void setProperty(Map<String, ?> map, String key, Object value) throws Exception {
		int i = key.indexOf('.');
		if (i > 0) {
			Object bean = map.get(key.substring(0, i));
			if (bean != null) {
				setProperty(bean, key.substring(i + 1), value);
			}
		} else {
			((Map<String, Object>)map).put(key, value);
		}
	}

	public static Object getProperty(Map<String, ?> map, String key) throws Exception {
		if (key == null) {
			return null;
		}
		Object value = map.get(key);
		if (value != null) {
			return value;
		}
		int i = key.indexOf('.');
		if (i > 0) {
			value = map.get(key.substring(0, i));
			if (value != null) {
				return getProperty(value, key.substring(i + 1));
			}
		}
		return null;
	}
	
	public static void setProperty(Object bean, String key, Object value) throws Exception {
		int i = key.indexOf('.');
		if (i > 0) {
			Object property;
			if (bean.getClass().isArray() && StringUtils.isInteger(key.substring(0, i))) {
				int index = Integer.parseInt(key.substring(0, i));
				if (index >= Array.getLength(bean)) {
					Object array = Array.newInstance(bean.getClass().getComponentType(), index + 1);
					System.arraycopy(bean, 0, array, 0, Array.getLength(bean));
					bean = array;
				}
				property = Array.get(bean, index);
				if (property == null) {
					property = bean.getClass().getComponentType().newInstance();
					Array.set(bean, index, value);
				}
			} else {
				Method getter = bean.getClass().getMethod("get" + key.substring(0, 1).toUpperCase() + key.substring(1, i), new Class<?>[0]);
				property = getter.invoke(bean, new Object[0]);
				if (property == null) {
					Method setter = getMethod(bean, "set" + key.substring(0, 1).toUpperCase() + key.substring(1, i));
					property = setter.getParameterTypes()[0].newInstance();
					setter.invoke(bean, new Object[]{ property });
				}
			}
			setProperty(property, key.substring(i + 1), value);
		} else {
			if (bean.getClass().isArray() && StringUtils.isInteger(key)) {
				int index = Integer.parseInt(key);
				if (index >= Array.getLength(bean)) {
					Object array = Array.newInstance(bean.getClass().getComponentType(), index + 1);
					System.arraycopy(bean, 0, array, 0, Array.getLength(bean));
					bean = array;
				}
				Array.set(bean, index, value);
			} else {
				Method setter = getMethod(bean, "set" + key.substring(0, 1).toUpperCase() + key.substring(1));
				setter.invoke(bean, new Object[]{ value });
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static Object getProperty(Object bean, String key) throws Exception {
		int i = key.indexOf('.');
		if (i > 0) {
			Object value = null;
			if (bean.getClass().isArray() && StringUtils.isInteger(key.substring(0, i))) {
				int index = Integer.parseInt(key.substring(0, i));
				if (index < Array.getLength(bean)) {
					value = Array.get(bean, index);
				}
			} else {
				if (bean instanceof Map) {
					value = ((Map<Object, Object>)bean).get(key.substring(0, 1).toLowerCase() + key.substring(1, i));
				} else {
					Method getter = bean.getClass().getMethod("get" + key.substring(0, 1).toUpperCase() + key.substring(1, i), new Class<?>[0]);
					value = getter.invoke(bean, new Object[0]);
				}
			}
			if (value == null) {
				return null;
			}
			return getProperty(value, key.substring(i + 1));
		} else {
			if (bean.getClass().isArray() && StringUtils.isInteger(key)) {
				int index = Integer.parseInt(key);
				if (index < Array.getLength(bean)) {
					return Array.get(bean, index);
				} else {
					return null;
				}
			} else {
				Method getter = bean.getClass().getMethod("get" + key.substring(0, 1).toUpperCase() + key.substring(1), new Class<?>[0]);
				return getter.invoke(bean, new Object[0]);
			}
		}
	}

	public static Method getMethod(Object model, String methodName) throws NoSuchMethodException {
		Class<?> clazz = model.getClass();
		for (Method method : clazz.getMethods()) {
			if (method.getName().equals(methodName)) {
				return method;
			}
		}
		throw new NoSuchMethodException("No such method " + methodName + " in class " 
				+ (clazz.getInterfaces().length > 0 ? clazz.getInterfaces()[0].getName() : clazz.getName()));
	}

	public static Object convertValue(Object value, Class<?> cls) throws Exception {
		if (value == null && ! cls.isPrimitive()) {
			return null;
		}
		if (String.class.equals(cls)) {
			return value == null ? null : String.valueOf(value);
		} else if (Character.class.equals(cls) || char.class.equals(cls)) {
			return value == null ? '\0' : String.valueOf(value).charAt(0);
		} else if (Boolean.class.equals(cls) || boolean.class.equals(cls)) {
			return value == null ? false : "true".equalsIgnoreCase(String.valueOf(value));
		} else if (Byte.class.equals(cls) || byte.class.equals(cls)) {
			return value == null ? 0 : Byte.valueOf(String.valueOf(value));
		} else if (Short.class.equals(cls) || short.class.equals(cls)) {
			return value == null ? 0 : Short.valueOf(String.valueOf(value));
		} else if (Integer.class.equals(cls) || int.class.equals(cls)) {
			return value == null ? 0 : Integer.valueOf(String.valueOf(value));
		} else if (Long.class.equals(cls) || long.class.equals(cls)) {
			return value == null ? 0 : Long.valueOf(String.valueOf(value));
		} else if (Float.class.equals(cls) || float.class.equals(cls)) {
			return value == null ? 0 : Float.valueOf(String.valueOf(value));
		} else if (Double.class.equals(cls) || double.class.equals(cls)) {
			return value == null ? 0 : Double.valueOf(String.valueOf(value));
		} else {
			return value;
		}
	}
	
	public static Object convertBean(Map<String, ?> parameters, Class<?> cls) throws Exception {
		return convertBean(parameters, cls, null);
	}

	public static Object convertBean(Map<String, ?> parameters, Class<?> cls, String key) throws Exception {
		Object value = parameters.get(key);
		if (cls.isPrimitive() || cls.getName().startsWith("java.lang.")) {
			return convertValue(value, cls);
		} else {
			if (value == null || ! cls.isInstance(value)) {
				if (key != null && key.length() > 0 
						&& ! hasPrefixParameter(parameters, key + ".")) {
					return null;
				}
				value = cls.newInstance();
				for (Method argMethod : cls.getMethods()) {
					String argMethodName = argMethod.getName();
					if (argMethodName.startsWith("set")
							&& Modifier.isPublic(argMethod.getModifiers())
							&& argMethod.getParameterTypes().length == 1) {
						String argKey = argMethodName.substring(3, 4).toLowerCase() + StringUtils.toCamelName(argMethodName.substring(4));
						if (key != null && key.length() > 0) {
							argKey = key + "." + argKey;
						}
						Object argResult = convertBean(parameters, argMethod.getParameterTypes()[0], argKey);
						argMethod.invoke(value, new Object[] { argResult });
					}
				}
			}
			return value;
		}
	}
	
	private static boolean hasPrefixParameter(Map<String, ?> parameters, String prefix) {
		for (String key : parameters.keySet()) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

    public static Class<?> forName(String name) {
        try {
			if (name == null || name.length() == 0)
				return null;
			int index = name.indexOf('[');
			if (index > 0) {
				int i = (name.length() - index) / 2;
				name = name.substring(0, index);
				StringBuilder sb = new StringBuilder();
				while (i-- > 0)
					sb.append("["); // int[][]
				if ("void".equals(name))
					sb.append("V");
				else if ("boolean".equals(name))
					sb.append("Z");
				else if ("byte".equals(name))
					sb.append("B");
				else if ("char".equals(name))
					sb.append("C");
				else if ("double".equals(name))
					sb.append("D");
				else if ("float".equals(name))
					sb.append("F");
				else if ("int".equals(name))
					sb.append("I");
				else if ("long".equals(name))
					sb.append("J");
				else if ("short".equals(name))
					sb.append("S");
				else
					sb.append('L').append(name).append(';');
				name = sb.toString();
			} else {
				if ("void".equals(name))
					return void.class;
				else if ("boolean".equals(name))
					return boolean.class;
				else if ("byte".equals(name))
					return byte.class;
				else if ("char".equals(name))
					return char.class;
				else if ("double".equals(name))
					return double.class;
				else if ("float".equals(name))
					return float.class;
				else if ("int".equals(name))
					return int.class;
				else if ("long".equals(name))
					return long.class;
				else if ("short".equals(name))
					return short.class;
				else if ("Void".equals(name))
					return Void.class;
				else if ("Boolean".equals(name))
					return Boolean.class;
				else if ("Byte".equals(name))
					return Byte.class;
				else if ("Char".equals(name))
					return Character.class;
				else if ("Double".equals(name))
					return Double.class;
				else if ("Float".equals(name))
					return Float.class;
				else if ("Integer".equals(name))
					return Integer.class;
				else if ("Long".equals(name))
					return Long.class;
				else if ("Short".equals(name))
					return Short.class;
				else if ("String".equals(name))
					return String.class;
			}
			return Class.forName(name, true, Thread.currentThread()
					.getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}
