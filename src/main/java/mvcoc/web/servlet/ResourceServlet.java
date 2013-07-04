package mvcoc.web.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

    private final long start = System.currentTimeMillis();
    
    private File rootDirectory;
    
    public void init() throws ServletException {
    	String directory = getServletConfig().getInitParameter("directory");
    	if (directory != null && directory.length() > 0) {
    		rootDirectory = new File(directory);
    	} else {
    		rootDirectory = new File(getServletContext().getRealPath("/"));
    	}
    }

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
        if (response.isCommitted()) {
            return;
        }
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (uri.endsWith("/favicon.ico")) {
            uri = "/favicon.ico";
        } else if (context != null && ! "/".equals(context)) {
            uri = uri.substring(context.length());
        }
        if (! uri.startsWith("/")) {
            uri = "/" + uri;
        }
        File file = new File(rootDirectory, uri);
        if (! file.exists()) {
        	response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        long lastModified = file.exists() ? file.lastModified() : start;
        long since = request.getDateHeader("If-Modified-Since");
        if (since >= lastModified) {
        	response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
        	return;
        }
        byte[] data;
        InputStream input = new FileInputStream(file);
    	try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n = 0;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
            }
            data = output.toByteArray();
        } finally {
            input.close();
        }
        response.setDateHeader("Last-Modified", lastModified);
        OutputStream output = response.getOutputStream();
        output.write(data);
        output.flush();
    }

}
