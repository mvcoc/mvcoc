package mvcoc.controllers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import mvcoc.Controller;
import mvcoc.Model;
import mvcoc.util.ClassUtils;
import mvcoc.util.IOUtils;

public class PropertiesController implements Controller {

	private static final Pattern PATH_SPLIT = Pattern.compile("[/]+");

	private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

	private final Map<String, Object> pathMapping = new ConcurrentHashMap<String, Object>();

	private final Map<String, List<String>> controllerProperties = new ConcurrentHashMap<String, List<String>>();

	private String indexPath;

	private String formPackage;

	private String viewDirectory;
	
	private String controllerDirectory;
	
	private ServletContext servletContext;

	private Model model;

	public void setModel(Model model) {
		this.model = model;
	}

	public void setProperties(Properties config) {
		indexPath = config.getProperty("controller.index.path");
		formPackage = config.getProperty("controller.form.package");
		controllerDirectory = config.getProperty("controller.directory");
		viewDirectory = config.getProperty("view.directory");
	    init();
	}
	
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
		init();
	}
	
	private void init() {
		if (servletContext != null) {
			if (controllerDirectory != null && controllerDirectory.length() > 0) {
				addDirectory(new File(servletContext.getRealPath(controllerDirectory)), pathMapping);
			}
		    if (viewDirectory != null && viewDirectory.length() > 0 
		    		&& ! viewDirectory.equals(controllerDirectory)) {
		    	addDirectory(new File(servletContext.getRealPath(viewDirectory)), pathMapping);
		    }
		}
	}

	@SuppressWarnings("unchecked")
	private void addDirectory(File directory, Map<String, Object> mapping) {
		File[] files = directory.listFiles();
		if (files == null || files.length == 0) {
			return;
		}
	    for (File file : files) {
	    	if (file.isHidden()) {
	    		continue;
	    	}
			String name = file.getName();
			if (name.length() == 0) {
				continue;
			}
			String var = null;
			if (file.isFile()) {
				int index = name.lastIndexOf('.');
				if (index > 0) {
					name = name.substring(0, index);
				}
			}
			if (name.startsWith("${")) {
				int index = name.indexOf('}');
				if (index > 0) {
					var = name.substring(2, index);
					name = "$";
				}
			}
			Map<String, Object> map = (Map<String, Object>) mapping.get(name);
			if (map == null) {
				map = new HashMap<String, Object>();
				mapping.put(name, map);
			}
			if (var != null && var.length() > 0) {
				map.put(".", var);
			}
	    	if (file.isDirectory()) {
	    		addDirectory(file, map);
	    	}
	    }
	}

	@SuppressWarnings("unchecked")
	public String execute(String action, String uri, Map<String, Object> models) throws Exception {
		if (uri.length() == 0 || "/".equals(uri)) {
			uri = indexPath;
		}
		String[] tokens = PATH_SPLIT.split(uri);
		StringBuilder pathBuilder = new StringBuilder();
		Map<String, Object> mapping = pathMapping;
		for (String token : tokens) {
			if (token.length() == 0) {
				continue;
			}
			Map<String, Object> value = (Map<String, Object>) mapping.get(token);
			if (value != null) {
				pathBuilder.append("/");
				pathBuilder.append(token);
			} else {
				value = (Map<String, Object>) mapping.get("$");
				if (value != null) {
					String var = (String) value.get(".");
					models.put(var, token);
					pathBuilder.append("/${");
					pathBuilder.append(var);
					pathBuilder.append("}");
				} else {
					throw new FileNotFoundException("No such " + uri);
				}
			}
			mapping = value;
		}
		String path = pathBuilder.toString();
		String controller = path + "." + action;
		try {
			List<String> properties = controllerProperties.get(controller);
			if (properties == null) {
				InputStream in = servletContext.getResourceAsStream(controllerDirectory + controller);
				if (in != null) {
					properties = IOUtils.readToLines(in);
				} else {
					properties = new ArrayList<String>();
				}
				controllerProperties.put(controller, properties);
			}
			Map<String, Object> parameters = new HashMap<String, Object>(models);
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				String key = entry.getKey();
				int i = key.indexOf(".");
				if (i > 0) {
					key = key.substring(0, i);
					if (! models.containsKey(key)) {
						String upper = key.substring(0, 1).toUpperCase() + key.substring(1);
						Object model = ClassUtils.convertBean(parameters, ClassUtils.forName(formPackage + "." + upper), key);
						models.put(key, model);
					}
				}
			}
			for (String line : properties) {
				int n = line.indexOf('=');
				String key = (line.substring(0, n)).trim();
				String value = (line.substring(n + 1)).trim();
				Object result;
				if (value.startsWith("\"") && value.endsWith("\"")) {
					result = value.substring(1, value.length() - 1);
				} else if (NUMBER_PATTERN.matcher(value).matches()) {
					result = Integer.valueOf(value);
				} else if (value.contains("(") && value.endsWith(")")) {
					int i = value.indexOf("(");
					int j = value.indexOf(".");
					if (j < 0 || j > i) {
						throw new IllegalStateException("Illegal " + value);
					}
					String modelName = value.substring(0, j);
					String methodName = value.substring(j + 1, i);
					String methodArgs = value.substring(i + 1, value.length() - 1);
					Object service = models.get(modelName);
					if (service == null) {
						service = model.get(modelName);
					}
					Method method = ClassUtils.getMethod(service, methodName);
					if (method.getParameterTypes().length == 0) {
						result = method.invoke(service, new Object[0]);
					} else if (method.getParameterTypes().length == 1) {
						Object arg = ClassUtils.convertValue(ClassUtils.getProperty(models, methodArgs), method.getParameterTypes()[0]);
						result = method.invoke(service, new Object[] { arg });
					} else {
						throw new IllegalStateException("Unsupported multi args in " + modelName + "." + methodName);
					}
				} else if (value.startsWith("new ")) {
					if (value.contains("[") && value.endsWith("]")) {
						int i = value.indexOf("[");
						String domainName = value.substring(4, i);
						Class<?> domanClass;
						try {
							domanClass = ClassUtils.forName(domainName);
						} catch (RuntimeException e) {
							if (domainName.indexOf('.') < 0) {
								domainName = formPackage  + "." + domainName;
								domanClass = ClassUtils.forName(domainName);
							} else {
								throw e;
							}
						}
						result = Array.newInstance(domanClass, Integer.parseInt(value.substring(i + 1, value.length() - 1)));
					} else {
						String domainName = value.substring(4);
						if (domainName.indexOf('.') < 0) {
							domainName = formPackage  + "." + domainName;
						}
						result = ClassUtils.forName(domainName).newInstance();
					}
				} else {
					result = ClassUtils.getProperty(models, value);
				}
				if (result != null) {
					ClassUtils.setProperty(models, key, result);
				}
			}
			String redirect = (String) models.get("redirect");
			if (redirect != null && redirect.length() > 0) {
				return "redirect:" + redirect;
			} else if ("get".equals(action)) {
				return path;
			} else {
				return "redirect:" + uri;
			}
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException) {
				e = ((InvocationTargetException) e).getTargetException();
			}
			if (! (e instanceof SecurityException)) {
				e.printStackTrace();
			}
			models.put("success", false);
			models.put("message", e.getMessage());
			String redirect = (String) models.get("redirect");
			if (redirect != null && redirect.length() > 0) {
				return "redirect:" + redirect;
			} else if (uri.endsWith("/add")) {
				return "redirect:" + uri.substring(0, uri.length() - "/add".length());
			} else if (uri.endsWith("/edit")) {
				return "redirect:" + uri.substring(0, uri.length() - "/edit".length());
			} else if (uri.endsWith("/delete")) {
				return "redirect:" + uri.substring(0, uri.length() - "/delete".length());
			} else {
				return "redirect:" + uri;
			}
		}
	}

	@Override
	public String get(String path, Map<String, Object> parameters) throws Exception {
		return execute("get", path, parameters);
	}

	@Override
	public String post(String path, Map<String, Object> parameters) throws Exception {
		return execute("post", path, parameters);
	}

	@Override
	public String put(String path, Map<String, Object> parameters) throws Exception {
		return execute("put", path, parameters);
	}

	@Override
	public String delete(String path, Map<String, Object> parameters) throws Exception {
		return execute("delete", path, parameters);
	}

}
