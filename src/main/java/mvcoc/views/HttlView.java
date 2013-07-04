package mvcoc.views;

import httl.web.WebEngine;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.text.ParseException;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import mvcoc.View;

public class HttlView implements View {

	private String viewExtension;

	public void setProperties(Properties config) {
		viewExtension = config.getProperty("view.extension");
	}
	
	public void setServletContext(ServletContext servletContext) {
		WebEngine.setServletContext(servletContext);
	}
	
	private String cleanPath(String path) {
		if (! path.endsWith(viewExtension)) {
			return path + viewExtension;
		}
		return path;
	}

	@Override
	public void render(String path, Map<String, Object> parameters, OutputStream stream) throws ParseException, IOException {
		WebEngine.getEngine().getTemplate(cleanPath(path)).render(parameters, stream);
	}

	@Override
	public void render(String path, Map<String, Object> parameters, Writer writer) throws ParseException, IOException {
		WebEngine.getEngine().getTemplate(cleanPath(path)).render(parameters, writer);
	}

	@Override
	public boolean has(String path) {
		return WebEngine.getEngine().hasResource(path);
	}

}
