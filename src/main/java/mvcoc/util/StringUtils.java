package mvcoc.util;

import java.util.regex.Pattern;

public class StringUtils {

	public static String toUnderlineName(String name) {
		if (name == null || name.length() == 0) {
    		return name;
    	}
    	StringBuilder buf = new StringBuilder(name.length() * 2);
    	buf.append(Character.toLowerCase(name.charAt(0)));
    	for (int i = 1; i < name.length(); i ++) {
    		char c = name.charAt(i);
    		if (c >= 'A' && c <= 'Z') {
    			buf.append('_');
    			buf.append(Character.toLowerCase(c));
    		} else {
    			buf.append(c);
    		}
    	}
    	return buf.toString();
	}

    public static String toCamelName(String name) {
    	if (name == null || name.length() == 0) {
    		return name;
    	}
    	StringBuilder buf = new StringBuilder(name.length());
    	boolean upper = false;
    	for (int i = 0; i < name.length(); i ++) {
    		char c = name.charAt(i);
    		if (c == '_') {
    			upper = true;
    		} else {
    			if (upper) {
    				upper = false;
    				c = Character.toUpperCase(c);
    			}
    			buf.append(c);
    		}
    	}
    	return buf.toString();
    }
    
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    
    public static boolean isInteger(String value) {
    	return INTEGER_PATTERN.matcher(value).matches();
    }

}
