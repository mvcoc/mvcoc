package mvcoc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.text.ParseException;
import java.util.Map;

public interface View {

	boolean has(String path);

	void render(String path, Map<String, Object> parameters, OutputStream output) throws ParseException, IOException;

	void render(String path, Map<String, Object> parameters, Writer writer) throws ParseException, IOException;

}
