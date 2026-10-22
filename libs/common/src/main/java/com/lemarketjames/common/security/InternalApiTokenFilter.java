package com.lemarketjames.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates server-to-server calls to {@code /internal/**} (BR-02: no endpoint without a
 * credential). The calling service sends the shared internal token in {@link #HEADER}; a match
 * grants {@link #AUTHORITY}, which each service's {@code SecurityConfig} requires on
 * {@code /internal/**}. Settlement there moves cash and shares for any account, so a browser
 * session, client or staff, never qualifies: it gets 403, and a request with no or a wrong token
 * gets 401.
 *
 * <p>A dedicated authority rather than a {@link Role}: a service is not a person, never holds a
 * JWT, and the roles in contract C7 stay the people's roles.
 */
public class InternalApiTokenFilter extends OncePerRequestFilter {

    /** The header the calling service puts the token in. */
    public static final String HEADER = "X-Internal-Token";

    /** Granted to a request that carries the right token. */
    public static final String AUTHORITY = "INTERNAL_SERVICE";

    private static final String INTERNAL_PREFIX = "/internal/";
    private static final Logger log = LoggerFactory.getLogger(InternalApiTokenFilter.class);

    private final byte[] token;

    /**
     * @param token the shared internal token ({@code internal.api-token}); a blank one stops the
     *              service from starting rather than leaving {@code /internal/**} unusable or open
     */
    public InternalApiTokenFilter(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("internal.api-token must be set");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !path(request).startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String sent = request.getHeader(HEADER);
        if (sent != null) {
            // Constant-time, so the response time says nothing about how much of the token matched.
            if (MessageDigest.isEqual(token, sent.getBytes(StandardCharsets.UTF_8))) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "internal-service", null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Never the token itself: only that a wrong one was sent, and where.
                log.warn("INTERNAL_TOKEN_REJECTED path={} remote={}", path(request), request.getRemoteAddr());
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
