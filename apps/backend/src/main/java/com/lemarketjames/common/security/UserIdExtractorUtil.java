package com.lemarketjames.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class to extract the authenticated user's username from the JWT token.
 * This is the single source of truth for getting the current user.
 */
public class UserIdExtractorUtil {

    /**
     * Extracts the username from the currently authenticated user's JWT token.
     * 
     * The SecurityContextHolder is set by JwtAuthenticationFilter after validating the JWT.
     * It contains the username that was decoded from the token.
     * 
     * @return the username of the authenticated user
     * @throws IllegalStateException if no user is authenticated
     */
    public static String extractAuthenticatedUsername() {
        // Step 1: Get the current authentication from Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Step 2: Check if authentication exists and is authenticated
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found in SecurityContextHolder");
        }
        
        // Step 3: Get the principal (username) from the authentication
        // The principal is the username that was set by JwtAuthenticationFilter
        Object principal = authentication.getPrincipal();
        
        // Step 4: Return as string
        return principal.toString();
    }
}
