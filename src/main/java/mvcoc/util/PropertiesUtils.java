package mvcoc.util;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

public class PropertiesUtils {

	public static int getInt(Properties properties, String key, int defaultValue) {
	    String value = properties.getProperty(key);
	    if (value == null || value.trim().length() == 0) {
	        return defaultValue;
	    }
	    return Integer.parseInt(value.trim());
	}

	public static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
	    String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equalsIgnoreCase(value);
	}

    public static boolean isTrue(final Object obj) {
        if (obj == null)
            return false;
        if (obj instanceof Boolean)
            return ((Boolean)obj).booleanValue();
        if (obj instanceof BigDecimal
                || obj instanceof Double
                || obj instanceof Float)
            return ((Number)obj).doubleValue() != 0;
        if (obj instanceof Number)
            return ((Number)obj).intValue() != 0;
        if (obj instanceof String)
            return ((String)obj).length() > 0;
        if (obj instanceof Collection<?>)
            return ((Collection<?>)obj).size() > 0;
        if (obj instanceof Map<?, ?>)
            return ((Map<?, ?>)obj).size() > 0;
        return true;
    }

}
