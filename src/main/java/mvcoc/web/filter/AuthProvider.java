package mvcoc.web.filter;

import java.security.Principal;

public interface AuthProvider {

	/**
	 * Get password.
	 * 
	 * @param username
	 * @param realm
	 * @return MD5(username + ":" + realm + ":" + password)
	 */
	String getPassword(String username, String realm);

	/**
	 * Get principal.
	 * 
	 * @param username
	 * @param realm
	 * @return principal
	 */
	Principal getPrincipal(String username, String realm);
	
	/**
	 * Is in role.
	 * 
	 * @param user
	 * @param role
	 * @return in role
	 */
	boolean isInRole(Principal user, String role);

}