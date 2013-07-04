package mvcoc;

public interface Model {

	<T> T get(Class<T> type, String name);

	<T> T get(Class<T> type);

	<T> T get(String name);

}
