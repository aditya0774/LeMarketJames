package com.lemarketjames.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Spring Security filter that extracts JWT tokens from cookies and authenticates requests.
 * Runs once per request to set up the security context with the authenticated user.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** The name of the cookie containing the JWT token */
    public static final String COOKIE_NAME = "jwt";

    private final JwtService jwtService;

    /**
     * Constructs a JwtAuthenticationFilter with the given JwtService.
     *
     * @param jwtService the JWT service for token validation
     */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Processes incoming requests to extract and validate JWT tokens from cookies.
     * Sets up authentication in the security context if a valid token is found.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain to pass control to next filter
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractTokenFromCookie(request)
                .flatMap(jwtService::extractUsername)
                .ifPresent(username -> {
                    var authentication = new UsernamePasswordAuthenticationToken(username, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the cookies in the request.
     *
     * @param request the HTTP request to extract the cookie from
     * @return an Optional containing the token value if found, or empty if no JWT cookie is present
     */
    private Optional<String> extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return java.util.Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
