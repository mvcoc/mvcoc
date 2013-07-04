package mvcoc.web.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import mvcoc.Controller;
import mvcoc.Model;
import mvcoc.View;
import mvcoc.util.ClassUtils;

public class DispatcherServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String CONFIG_KEY = "config";

	private static final String MODEL_KEY = "model";

	private static final String VIEW_KEY = "view";

	private static final String CONTROLLER_KEY = "controller";

	private static final String DEFAULT_CONFIG = "/WEB-INF/mvcoc.properties";

	private static final String REDIRECT_PREFIX = "redirect:";

	private static final String PROTOCOL_SEPARATOR = "://";

	private static Model MODEL;

	private static View VIEW;

	private static Controller CONTROLLER;

	private String viewExtension;

	private String layoutName;

	private String layoutPlaceholder;
	
	private String redirectPath;

	public static Model getModel() {
		return MODEL;
	}

	public static View getView() {
		return VIEW;
	}

	public static Controller getController() {
		return CONTROLLER;
	}

	@Override
	public void init() throws ServletException {
	    super.init();
	    String config = getServletConfig().getInitParameter(CONFIG_KEY);
	    if (config == null || config.length() == 0) {
	    	config = DEFAULT_CONFIG;
	    }
	    Properties properties = new Properties();
	    try {
	    	InputStream in = null;
	    	if (config.startsWith("/")) {
		    	in = getServletContext().getResourceAsStream(config);
	    	} else {
	    		in = Thread.currentThread().getContextClassLoader().getResourceAsStream(config);
	    	}
	    	if (in == null) {
	    		throw new FileNotFoundException("Not found mvcoc config " + config);
	    	}
			properties.load(in);
		} catch (IOException e) {
			throw new ServletException(e.getMessage(), e);
		}
	    MODEL = (Model) newInstance(properties, MODEL_KEY);
	    VIEW = (View) newInstance(properties, VIEW_KEY);
	    CONTROLLER = (Controller) newInstance(properties, CONTROLLER_KEY);
	    
	    viewExtension = properties.getProperty("view.extension");
	    layoutName = properties.getProperty("view.layout.path");
		layoutPlaceholder = properties.getProperty("view.layout.placeholder");
		
		redirectPath = properties.getProperty("controller.redirect.path");
	}
	
	private Object newInstance(Properties config, String key) throws ServletException {
		try {
			Class<?> clazz = (Class<?>) ClassUtils.forName(config.getProperty(key));
			Object instance = clazz.newInstance();
			try {
				clazz.getMethod("setProperties", new Class<?>[] { Properties.class })
					.invoke(instance, new Object[] { config });
				clazz.getMethod("setServletContext", new Class<?>[] { ServletContext.class })
					.invoke(instance, new Object[] { getServletContext() });
				clazz.getMethod("setModel", new Class<?>[] { Model.class })
					.invoke(instance, new Object[] { MODEL });
				clazz.getMethod("setView", new Class<?>[] { View.class })
					.invoke(instance, new Object[] { VIEW });
			} catch (NoSuchMethodException e) {
			}
			return instance;
		} catch (Exception e) {
			throw new ServletException(e.getMessage(), e);
		}
	}
	
    @Override
    public void destroy() {
        super.destroy();
    }

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		process("post", request, response);
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		process("get", request, response);
	}

	@Override
	protected void doPut(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		process("put", request, response);
	}

	@Override
	protected void doDelete(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		process("delete", request, response);
	}
	
	@SuppressWarnings("unchecked")
	protected void process(String action, HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		try {
			request.setCharacterEncoding("UTF-8");
			response.setCharacterEncoding("UTF-8");
			response.setContentType("text/html; charset=UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			String uri = request.getRequestURI();
			String ctx = request.getContextPath();
			if (ctx == null || "/".equals(ctx)) {
				ctx = "";
			} else if (uri.startsWith(ctx)) {
				uri = uri.substring(ctx.length());
			}
			Map<String, Object> models = new HashMap<String, Object>();
			models.put("request", request);
			models.put("response", response);
			models.put("userPrincipal", request.getUserPrincipal());
			models.put("contextPath", ctx);
			models.put("requestURI", uri);
			for (Map.Entry<String, String[]> entry : ((Map<String, String[]>)request.getParameterMap()).entrySet()) {
				String[] values = entry.getValue();
				if (values.length > 0) {
					models.put(entry.getKey(), values.length > 1 ? values : values[0]);
				}
			}
			String path = execute(action, uri, models);
			if (path == null || path.length() == 0) {
				if ("get".equals(action)) {
					path = uri;
				}  else {
					path = REDIRECT_PREFIX + uri;
				}
			}
			if (path.startsWith(REDIRECT_PREFIX)) {
				path = path.substring(REDIRECT_PREFIX.length());
				if (ctx != null && ctx.length() > 0 
						&& path.startsWith("/") && ! path.startsWith(ctx)) {
					path = ctx + path;
				}
				if (redirectPath != null && redirectPath.length() > 0) {
					models.put("redirect", path);
					VIEW.render(redirectPath + viewExtension, models, response.getWriter());
					response.flushBuffer();
				} else {
					response.sendRedirect(path);
				}
			} else if (path.contains(PROTOCOL_SEPARATOR)) {
				response.sendRedirect(path);
			} else {
				Writer outputStream = response.getWriter();
				String layoutUriDir = uri;
				String layoutPathDir = path;
				for(;;) {
					int i = layoutUriDir.lastIndexOf('/');
					int j = layoutPathDir.lastIndexOf('/');
					if (i < 0 || j < 0) {
						break;
					}
					layoutUriDir = layoutUriDir.substring(0, i);
					layoutPathDir = layoutPathDir.substring(0, j);
					if (VIEW.has(layoutPathDir + layoutName + viewExtension)) {
						StringWriter out = new StringWriter();
						VIEW.render(path, models, out);
						models.put(layoutPlaceholder, out.toString());
						path = execute(action, layoutUriDir + layoutName, models);
					}
				}
				VIEW.render(path, models, outputStream);
				response.flushBuffer();
			}
		} catch (SecurityException e) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
		} catch (Throwable e) {
			throw new ServletException(e.getMessage(), e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, Object> toModel(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String ctx = request.getContextPath();
		if (ctx == null || "/".equals(ctx)) {
			ctx = "";
		} else if (uri.startsWith(ctx)) {
			uri = uri.substring(ctx.length());
		}
		Map<String, Object> models = new HashMap<String, Object>();
		models.put("request", request);
		models.put("userPrincipal", request.getUserPrincipal());
		models.put("contextPath", ctx);
		models.put("requestURI", uri);
		for (Map.Entry<String, String[]> entry : ((Map<String, String[]>)request.getParameterMap()).entrySet()) {
			String[] values = entry.getValue();
			if (values.length > 0) {
				models.put(entry.getKey(), values.length > 1 ? values : values[0]);
			}
		}
		return models;
	}
	
	private String execute(String action, String uri, Map<String, Object> models) throws Exception {
		String path = null;
		if ("get".equals(action)) {
			path = CONTROLLER.get(uri, models);
		} else if ("post".equals(action)) {
			path = CONTROLLER.post(uri, models);
		} else if ("put".equals(action)) {
			path = CONTROLLER.put(uri, models);
		} else if ("delete".equals(action)) {
			path = CONTROLLER.delete(uri, models);
		}
		return path;
	}
	
}
