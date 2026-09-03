package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtAuthenticationFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for handling authentication-related endpoints.
 * Provides endpoints for user registration, login, logout, and retrieving current user information.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Constructs an AuthController with the given AuthService.
     *
     * @param authService the authentication service to use
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
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

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("username", result.getUsername(), "message", result.getMessage()));
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
        return ResponseEntity.ok(Map.of("username", authentication.getName()));
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
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
