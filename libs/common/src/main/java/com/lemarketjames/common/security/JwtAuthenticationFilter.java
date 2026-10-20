package com.lemarketjames.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Security filter that extracts JWT tokens from cookies and authenticates requests.
 * Runs once per request to set up the security context with the authenticated user.
 *
 * <p>A valid signature is not enough on its own: the account behind the token is checked on every
 * request, so a session opened before the account was closed, suspended, expired, deleted or
 * deactivated ends on its next request with {@code 401 ACCOUNT_INACTIVE} (LMKT-57).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** The name of the cookie containing the JWT token */
    public static final String COOKIE_NAME = "jwt";

    // Server-to-server and health calls are not a person's session, so they are never refused here.
    private static final List<String> UNCHECKED_PREFIXES = List.of("/internal/", "/actuator/");

    // A stale cookie from a closed account must not stop anyone signing in, registering or signing out.
    private static final Set<String> UNCHECKED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/logout");

    // The constant parts need no escaping, so the body is built without a JSON library.
    private static final String REVOKED_BODY = "{\"success\":false,\"code\":\"" + AccountInactiveException.CODE
            + "\",\"error\":\"" + AccountInactiveException.MESSAGE + "\"}";

    private final JwtService jwtService;
    private final AccountAccessCheck accessCheck;
    private final Clock clock;

    /**
     * Constructs a JwtAuthenticationFilter.
     *
     * @param jwtService  the JWT service for token validation
     * @param accessCheck decides whether the token's account may still be used
     */
    public JwtAuthenticationFilter(JwtService jwtService, AccountAccessCheck accessCheck) {
        this(jwtService, accessCheck, Clock.systemUTC());
    }

    /** Takes the clock the security event is stamped with, so tests can fix the time. */
    JwtAuthenticationFilter(JwtService jwtService, AccountAccessCheck accessCheck, Clock clock) {
        this.jwtService = jwtService;
        this.accessCheck = accessCheck;
        this.clock = clock;
    }

    /**
     * Processes incoming requests to extract and validate JWT tokens from cookies.
     * Sets up authentication in the security context if a valid token is found and its account
     * may still be used; answers 401 itself if it may not.
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
        Optional<TokenClaims> claims = extractTokenFromCookie(request).flatMap(jwtService::extractClaims);
        if (claims.isPresent()) {
            TokenClaims token = claims.get();
            if (isChecked(request)) {
                Optional<String> revoked = accessCheck.revokedReason(token.username(), token.roles(), token.issuedAt());
                if (revoked.isPresent()) {
                    reject(token.username(), revoked.get(), response);
                    return;
                }
            }
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(TokenClaims claims) {
        // Roles become ROLE_* authorities so endpoints can guard with hasRole(...).
        List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(claims.username(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // Logged once per rejected request, then the request goes no further.
    private void reject(String username, String reason, HttpServletResponse response) throws IOException {
        SecurityEventLog.accessRevoked(username, reason, clock.instant());
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(REVOKED_BODY);
    }

    private boolean isChecked(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !UNCHECKED_PATHS.contains(path) && UNCHECKED_PREFIXES.stream().noneMatch(path::startsWith);
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
