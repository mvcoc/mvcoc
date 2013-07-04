package mvcoc;

import java.util.Map;

public interface Controller {

	String get(String uri, Map<String, Object> parameters) throws Exception;

	String post(String uri, Map<String, Object> parameters) throws Exception;

	String put(String uri, Map<String, Object> parameters) throws Exception;

	String delete(String uri, Map<String, Object> parameters) throws Exception;

}
