package mvcoc.web.filter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import mvcoc.util.StringUtils;

public class AuthFilter implements Filter {

    private static final String BASIC_CHALLENGE  = "Basic";

    private static final String DIGEST_CHALLENGE = "Digest";

    private static final String COOKIE_CHALLENGE = "Cookie";

    private String challenge = DIGEST_CHALLENGE;

    private String realm;

	private int remember;

	private String loginCookie;

	private String authorizationCookie;

	private AuthProvider authorizationProvider;

	public void init(FilterConfig config) throws ServletException {
		challenge = config.getInitParameter("challenge");
		if (challenge == null || challenge.length() == 0) {
			challenge = DIGEST_CHALLENGE;
		} else if (! BASIC_CHALLENGE.equals(challenge)
				&& ! DIGEST_CHALLENGE.equals(challenge)) {
			throw new ServletException("Unsupported challenge " + challenge + ", only supported " + BASIC_CHALLENGE + " or " + DIGEST_CHALLENGE);
		}
		realm = config.getInitParameter("realm");
		String r = config.getInitParameter("remember");
		if (r != null && r.length() > 0) {
			remember = Integer.valueOf(r);
		}
		loginCookie = config.getInitParameter("login.cookie");
		if (loginCookie == null || loginCookie.length() == 0) {
			loginCookie = "login";
		}
		authorizationCookie = config.getInitParameter("authorization.cookie");
		if (authorizationCookie == null || authorizationCookie.length() == 0) {
			authorizationCookie = "authorization";
		}
		try {
			String provider = config.getInitParameter(AuthProvider.class.getName());
			if (provider == null || provider.length() == 0) {
				throw new ServletException("Please config password provider, eg: \n" +
						"<filter>\n" +
						"	<filter-name>" + AuthFilter.class.getSimpleName() + "</filter-name>\n" +
						"	<filter-class>" + AuthFilter.class.getName() + "</filter-class>\n" +
						"	<init-param>\n" +
						"		<param-name>" + AuthProvider.class.getName() + "</param-name>\n" +
						"		<param-value>com.your.Your" + AuthProvider.class.getSimpleName() + "</param-value>\n" +
						"	</init-param>\n" +
						"</filter>\n");
			}
			authorizationProvider = (AuthProvider) Class.forName(provider).newInstance();
		} catch (Exception e) {
			throw new ServletException(e.getMessage(), e);
		}
	}

	public void destroy() {
	}
	
	private void initRealm(HttpServletRequest request) {
		if (realm == null || realm.length() == 0) {
			String url = request.getRequestURL().toString();
			int i = url.indexOf("://");
			if (i > 0) {
				url = url.substring(i + 3);
			}
			i = url.indexOf("/");
			if (i > 0) {
				url = url.substring(0, i);
			}
			i = url.lastIndexOf(".");
			if (i > 0) {
				url = url.substring(0, i);
				i = url.lastIndexOf(".");
				if (i > 0) {
					url = url.substring(i + 1);
				}
			}
			realm = url;
		}
	}
	
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		doFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
	}

	private void doFilter(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws IOException, ServletException {
        String uri = request.getRequestURI();
        if (uri.endsWith(".js") || uri.endsWith(".css")
        		|| uri.endsWith(".png") || uri.endsWith(".jpg")
        		|| uri.endsWith(".gif") || uri.endsWith(".ico")) {
        	chain.doFilter(request, response);
        	return;
        }
		String contextPath = request.getContextPath();
		if (contextPath != null && contextPath.length() > 0  && ! "/".equals(contextPath)) {
		    uri = uri.substring(contextPath.length());
		}
		initRealm(request);
		String challenge = this.challenge;
        String authType = challenge;
        String username = null;
        int loginCount = getIntCookie(request, loginCookie);
		if (loginCount > 0) {
			int loginMax = 1;
			// fix safari logout
			String userAgent = request.getHeader("User-Agent");
			if (userAgent != null && userAgent.contains("Safari") 
					&& ! userAgent.contains("Chrome")) {
				String authorization = getCookie(request, authorizationCookie);
	        	if (authorization != null && authorization.length() > 0) {
	        		loginMax = 2;
	        	}
			}
			if (loginCount >= loginMax) {
				removeCookie(response, loginCookie);
		    	removeCookie(response, authorizationCookie);
			} else {
				setCookie(response, loginCookie, String.valueOf(loginCount + 1));
			}
			showLoginForm(response, challenge);
		    return;
	    }
		String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.length() > 0) {
            int i = authorization.indexOf(' ');
            if (i >= 0) {
            	challenge = authorization.substring(0, i);
            	authorization = authorization.substring(i + 1);
                if (BASIC_CHALLENGE.equalsIgnoreCase(challenge)) {
                    username = loginByBase(authorization);
                } else if (DIGEST_CHALLENGE.equalsIgnoreCase(challenge)) {
                    username = loginByDigest(authorization, request);
                }
                if (username == null || username.length() == 0) {
                	response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                if (remember > 0) {
                	String nonce = UUID.randomUUID().toString().replace("-", "");
                	String password = authorizationProvider.getPassword(username, realm);
                	String identity = encodeMd5(request.getHeader("User-Agent") + ":" + request.getLocale());
                	setCookie(response, authorizationCookie, username + ":" + nonce + ":" + encodeMd5(password + ":" + nonce + ":" + identity), remember);
                }
            }
        } else {
        	authType = COOKIE_CHALLENGE;
        	authorization = getCookie(request, authorizationCookie);
        	if (authorization != null && authorization.length() > 0) {
        		username = loginByCookie(authorization, request);
        	}
        }
        chain.doFilter(new AuthRequest(request, authorizationProvider, authorizationProvider.getPrincipal(username, realm), authType), response);
    }

    private void showLoginForm(HttpServletResponse response, String challenge) throws IOException {
        if (DIGEST_CHALLENGE.equals(challenge)) {
            response.setHeader("WWW-Authenticate", challenge + " realm=\"" + realm + "\", qop=\"auth\", nonce=\""
                                                   + UUID.randomUUID().toString().replace("-", "") + "\", opaque=\""
                                                   + encodeMd5(realm) + "\"");
        } else {
            response.setHeader("WWW-Authenticate", challenge + " realm=\"" + realm + "\"");
        }
        response.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        response.setHeader("Content-Type", "text/html; charset=iso-8859-1");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private String loginByCookie(String authorization, HttpServletRequest request) {
    	String[] tokens = authorization.split(":");
    	if (tokens.length > 2) {
	    	String username = tokens[0];
	    	String nonce = tokens[1];
	    	String password = tokens[2];
	        if (username != null && username.length() > 0 
	        		&& nonce != null && nonce.length() > 0
	        		&& password != null && password.length() > 0) {
	            String pwd = authorizationProvider.getPassword(username, realm);
	            if (pwd != null && pwd.length() > 0) {
	            	String identity = encodeMd5(request.getHeader("User-Agent") + ":" + request.getLocale());
	                if (password.equals(encodeMd5(pwd + ":" + nonce + ":" + identity))) {
	                    return username;
	                }
	            }
	        }
    	}
        return null;
    }

    private String loginByBase(String authorization) {
        authorization = decodeBase64(authorization);
        int i = authorization.indexOf(':');
        String username = authorization.substring(0, i);
        if (username != null && username.length() > 0) {
            String password = authorization.substring(i + 1);
            if (password != null && password.length() > 0) {
                String passwordDigest = encodeMd5(username + ":" + realm + ":" + password);
                String pwd = authorizationProvider.getPassword(username, realm);
                if (pwd != null && pwd.length() > 0) {
                    if (passwordDigest.equals(pwd)) {
                        return username;
                    }
                }
            }
        }
        return null;
    }

    private String loginByDigest(String authorization, HttpServletRequest request) throws IOException {
        Map<String, String> params = parseDigestParameters(authorization);
        String username = params.get("username");
        if (username != null && username.length() > 0) {
            String passwordDigest = params.get("response");
            if (passwordDigest != null && passwordDigest.length() > 0) {
            	String pwd = authorizationProvider.getPassword(username, realm);
                if (pwd != null && pwd.length() > 0) {
                    String uri = params.get("uri");
                    String nonce = params.get("nonce");
                    String nc = params.get("nc");
                    String cnonce = params.get("cnonce");
                    String qop = params.get("qop");
                    String method = request.getMethod();
                    String a1 = pwd;

                    String a2 = "auth-int".equals(qop)
                        ? encodeMd5(method + ":" + uri + ":" + encodeMd5(readToBytes(request.getInputStream())))
                        : encodeMd5(method + ":" + uri);
                    String digest = "auth".equals(qop) || "auth-int".equals(qop)
                        ? encodeMd5(a1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + a2)
                        : encodeMd5(a1 + ":" + nonce + ":" + a2);
                    if (digest.equals(passwordDigest)) {
                        return username;
                    }
                }
            }
        }
        return null;
    }
    
    private int getIntCookie(HttpServletRequest request, String key) {
    	String value = getCookie(request, key);
    	if (value == null || value.length() == 0) {
    		return 0;
    	}
    	if (! StringUtils.isInteger(value)) {
    		if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
        		return 1;
        	} else {
        		return 0;
        	}
    	}
    	return Integer.parseInt(value);
    }

    private String getCookie(HttpServletRequest request, String key) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (cookie != null && key.equals(cookie.getName())) {
                	return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void removeCookie(HttpServletResponse response, String key) {
    	setCookie(response, key, "", 0);
    }

    private void setCookie(HttpServletResponse response, String key, String value) {
    	setCookie(response, key, value, -1);
    }

    private void setCookie(HttpServletResponse response, String key, String value, int age) {
    	Cookie cookie = new Cookie(key, value);
    	cookie.setMaxAge(age);
    	cookie.setPath("/");
        response.addCookie(cookie);
    }

    private static Pattern PARAMETER_PATTERN = Pattern.compile("(\\w+)=[\"]?([^,\"]+)[\"]?[,]?\\s*");

    static Map<String, String> parseDigestParameters(String query) {
        Matcher matcher = PARAMETER_PATTERN.matcher(query);
        Map<String, String> map = new HashMap<String, String>();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            map.put(key, value);
        }
        return map;
    }

    static byte[] readToBytes(InputStream in) throws IOException {
        byte[] buf = new byte[in.available()];
        in.read(buf);
        return buf;
    }
    
    static String encodeHex(byte[] bytes) {
		StringBuilder buffer = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			if (((int) bytes[i] & 0xff) < 0x10)
				buffer.append("0");
			buffer.append(Long.toString((int) bytes[i] & 0xff, 16));
		}
		return buffer.toString();
	}
    
    static String encodeMd5(String source) {
    	return encodeMd5(source.getBytes());
    }

	static String encodeMd5(byte[] source) {
		try {
			return encodeHex(MessageDigest.getInstance("MD5").digest(source));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	static String decodeBase64(String source) {
		return decodeBase64(source.getBytes());
	}

	static String decodeBase64(byte[] source) {
		try {
			return _decodeBase64(source);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
	
	static String _decodeBase64(byte[] source) throws IOException {
        ByteArrayInputStream inStream;
        ByteArrayOutputStream outStream;
        inStream = new ByteArrayInputStream(source);
        outStream = new ByteArrayOutputStream();
        int     i;
        int bytesPerAtom = 4;
        PushbackInputStream ps = new PushbackInputStream (inStream);
        while (true) {
            int length;
            try {
                length = 72;
                for (i = 0; (i+bytesPerAtom) < length; i += bytesPerAtom) {
                    decodeAtom(ps, outStream, bytesPerAtom);
                }
                if ((i + bytesPerAtom) == length) {
                    decodeAtom(ps, outStream, bytesPerAtom);
                } else {
                    decodeAtom(ps, outStream, length - i);
                }
            } catch (EOFException e) {
                break;
            }
        }
        return new String(outStream.toByteArray());
	}

    /**
     * This character array provides the character to value map
     * based on RFC1521.
     */
    private final static char pem_array[] = {
        //       0   1   2   3   4   5   6   7
                'A','B','C','D','E','F','G','H', // 0
                'I','J','K','L','M','N','O','P', // 1
                'Q','R','S','T','U','V','W','X', // 2
                'Y','Z','a','b','c','d','e','f', // 3
                'g','h','i','j','k','l','m','n', // 4
                'o','p','q','r','s','t','u','v', // 5
                'w','x','y','z','0','1','2','3', // 6
                '4','5','6','7','8','9','+','/'  // 7
        };

    private final static byte pem_convert_array[] = new byte[256];

    static {
        for (int i = 0; i < 255; i++) {
            pem_convert_array[i] = -1;
        }
        for (int i = 0; i < pem_array.length; i++) {
            pem_convert_array[pem_array[i]] = (byte) i;
        }
    }

    static void decodeAtom(PushbackInputStream inStream, OutputStream outStream, int rem) throws java.io.IOException {
    	
    	byte decode_buffer[] = new byte[4];
    	
        int     i;
        byte    a = -1, b = -1, c = -1, d = -1;

        if (rem < 2) {
            throw new IllegalStateException("BASE64Decoder: Not enough bytes for an atom.");
        }
        do {
            i = inStream.read();
            if (i == -1) {
                throw new EOFException("BASE64Decoder: Stream exhausted");
            }
        } while (i == '\n' || i == '\r');
        decode_buffer[0] = (byte) i;

        i = readFully(inStream, decode_buffer, 1, rem-1);
        if (i == -1) {
            throw new EOFException("BASE64Decoder: Stream exhausted");
        }

        if (rem > 3 && decode_buffer[3] == '=') {
            rem = 3;
        }
        if (rem > 2 && decode_buffer[2] == '=') {
            rem = 2;
        }
        switch (rem) {
        case 4:
            d = pem_convert_array[decode_buffer[3] & 0xff];
            // NOBREAK
        case 3:
            c = pem_convert_array[decode_buffer[2] & 0xff];
            // NOBREAK
        case 2:
            b = pem_convert_array[decode_buffer[1] & 0xff];
            a = pem_convert_array[decode_buffer[0] & 0xff];
            break;
        }

        switch (rem) {
        case 2:
            outStream.write( (byte)(((a << 2) & 0xfc) | ((b >>> 4) & 3)) );
            break;
        case 3:
            outStream.write( (byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)) );
            outStream.write( (byte) (((b << 4) & 0xf0) | ((c >>> 2) & 0xf)) );
            break;
        case 4:
            outStream.write( (byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)) );
            outStream.write( (byte) (((b << 4) & 0xf0) | ((c >>> 2) & 0xf)) );
            outStream.write( (byte) (((c << 6) & 0xc0) | (d  & 0x3f)) );
            break;
        }
        return;
    }

    static int readFully(InputStream in, byte buffer[], int offset, int len)
        throws java.io.IOException {
        for (int i = 0; i < len; i++) {
            int q = in.read();
            if (q == -1)
                return ((i == 0) ? -1 : i);
            buffer[i+offset] = (byte)q;
        }
        return len;
    }

	public static class AuthRequest extends HttpServletRequestWrapper {
		
		private AuthProvider authorizationProvider;
	
		private final Principal userPrincipal;
	
		private final String authType;
	
		public AuthRequest(HttpServletRequest request, AuthProvider authorizationProvider, Principal userPrincipal, String authType) {
			super(request);
			this.authorizationProvider = authorizationProvider;
			this.userPrincipal = userPrincipal;
			this.authType = authType;
		}
	
		@Override
		public String getAuthType() {
			return authType;
		}
	
		@Override
		public boolean isUserInRole(String role) {
			return authorizationProvider.isInRole(userPrincipal, role);
		}
	
		@Override
		public Principal getUserPrincipal() {
			return userPrincipal;
		}
	
		@Override
		public String getRemoteUser() {
			return userPrincipal == null ? null : userPrincipal.getName();
		}
	
		public String getUserAgent() {
			return getHeader("User-Agent");
		}
	
		public String getReferer() {
			return getHeader("Referer");
		}
	
	}
}
