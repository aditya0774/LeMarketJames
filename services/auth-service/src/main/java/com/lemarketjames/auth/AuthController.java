package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller for handling authentication-related endpoints.
 * Provides endpoints for user registration, login, logout, and retrieving current user information.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    /**
     * Constructs an AuthController with the given AuthService.
     *
     * @param authService the authentication service to use
     * @param secureCookie whether the auth cookie is marked Secure (HTTPS-only); disable only for plain-HTTP environments
     */
    public AuthController(AuthService authService,
                          @Value("${auth.cookie.secure:true}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    /**
     * Registers a new user with the provided registration request.
     *
     * @param request the registration request containing user details
     * @return a response entity with the registration result (username and message)
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    /**
     * Authenticates a user with the provided login credentials.
     * Returns a JWT token in an HTTP-only cookie.
     *
     * @param request the login request containing username and password
     * @return a response entity with the login result and JWT cookie
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        ResponseCookie cookie = buildAuthCookie(result.getToken(), authService.getTokenExpirySeconds());

        Map<String, Object> body = sessionBody(result.getUsername(), result.getRoles());
        body.put("message", result.getMessage());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    /**
     * Logs out the current user by clearing the JWT authentication cookie.
     *
     * @return a response entity with a logout confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cookie = buildAuthCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "Logged out"));
    }

    /**
     * Retrieves information about the currently authenticated user.
     *
     * @param authentication the current user's authentication principal
     * @return a response entity containing the username of the current user
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        Set<Role> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().substring("ROLE_".length()))
                .map(Role::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        return ResponseEntity.ok(sessionBody(authentication.getName(), roles));
    }

    /**
     * What the frontend needs to know about a session (contract C7): who, which roles, and, for
     * clients only, the trading account. Staff have no account, so accountId is left out for them.
     */
    private Map<String, Object> sessionBody(String username, Set<Role> roles) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("roles", roles.stream().map(Role::name).sorted().toList());
        if (roles.contains(Role.CLIENT)) {
            body.put("accountId", authService.getAccountId(username));
        }
        return body;
    }

    /**
     * Builds an HTTP-only, secure authentication cookie containing the JWT token.
     *
     * @param token the JWT token to store in the cookie
     * @param maxAgeSeconds the maximum age of the cookie in seconds
     * @return a ResponseCookie configured with appropriate security settings
     */
    private ResponseCookie buildAuthCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
