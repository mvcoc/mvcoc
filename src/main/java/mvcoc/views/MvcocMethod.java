package mvcoc.views;

import httl.spi.resolvers.ServletResolver;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.Map;

import mvcoc.web.servlet.DispatcherServlet;

public class MvcocMethod {

	private MvcocMethod() {}

	public static Object embed(String name) throws Exception {
		Map<String, Object> models = DispatcherServlet.toModel(ServletResolver.getRequest());
		String path = DispatcherServlet.getController().get(name, models);
		//ByteArrayOutputStream out = new ByteArrayOutputStream();
		StringWriter writer = new StringWriter();
		DispatcherServlet.getView().render(path, models, writer);
		//return out.toByteArray();
		return writer.toString();
	}

}
