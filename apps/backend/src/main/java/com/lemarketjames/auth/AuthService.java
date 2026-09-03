package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class providing authentication and authorization business logic.
 * Handles user registration, login, password encoding, and token generation.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final Map<String, String> userStore = new ConcurrentHashMap<>();
    private final Set<String> registeredEmails = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long lockoutDurationMs;

    /**
     * Constructs an AuthService with the given JwtService.
     * Initializes the password encoder and user store.
     *
     * @param jwtService the JWT service for token generation and validation
     */
    public AuthService(JwtService jwtService, @Value("${auth.lockout-duration-ms:900000}") long lockoutDurationMs) {
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtService = jwtService;
        this.lockoutDurationMs = lockoutDurationMs;
    }

    /**
     * Registers a new user with the provided registration request.
     * Validates the request, checks for duplicate usernames, and stores the user with an encoded password.
     *
     * @param request the registration request containing user details
     * @return an AuthResponse with the registered username and success message
     * @throws IllegalArgumentException if validation fails or username is already taken
     */
    public AuthResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername();
        String password = request.getPassword();
        String email = request.getEmail().toLowerCase();

        if (userStore.containsKey(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (!registeredEmails.add(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        userStore.put(username, encodePassword(password));
        log.info("Registration succeeded for username={}", username);
        return new AuthResponse(username, "User registered successfully");
    }

    /**
     * Authenticates a user with the provided login credentials.
     * Validates the request and verifies the password against the stored hash.
     *
     * @param request the login request containing username and password
     * @return a LoginResult with the username, success message, and JWT token
     * @throws IllegalArgumentException if validation fails or credentials are invalid
     */
    public LoginResult login(LoginRequest request) {
        validateLoginRequest(request);

        String username = request.getUsername();
        String password = request.getPassword();

        Instant lockExpiry = lockedUntil.get(username);
        if (lockExpiry != null) {
            if (Instant.now().isBefore(lockExpiry)) {
                log.warn("Login blocked for username={} due to active lockout", username);
                throw new IllegalArgumentException("Account temporarily locked due to too many failed attempts. Try again later.");
            }
            lockedUntil.remove(username);
            failedLoginAttempts.remove(username);
        }

        String storedPassword = userStore.get(username);

        if (storedPassword == null || !matchesPassword(password, storedPassword)) {
            registerFailedAttempt(username);
            log.warn("Login failed for username={}", username);
            throw new IllegalArgumentException("Invalid username or password");
        }

        failedLoginAttempts.remove(username);
        String token = jwtService.generateToken(username);
        log.info("Login succeeded for username={}", username);
        return new LoginResult(username, "Login successful", token);
    }

    // Tracks a failed login and locks the account once MAX_FAILED_ATTEMPTS is reached
    private void registerFailedAttempt(String username) {
        int attempts = failedLoginAttempts.merge(username, 1, Integer::sum);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil.put(username, Instant.now().plusMillis(lockoutDurationMs));
        }
    }

    /**
     * Encodes a raw password using BCrypt with a salt.
     *
     * @param rawPassword the plain text password to encode
     * @return the encoded password hash
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verifies that a raw password matches the encoded password hash.
     *
     * @param rawPassword the plain text password to verify
     * @param encodedPassword the encoded password hash to compare against
     * @return true if the passwords match, false otherwise
     */
    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * Retrieves the JWT token expiration time in seconds.
     *
     * @return the token expiration time in seconds
     */
    public long getTokenExpirySeconds() {
        return jwtService.getExpirationSeconds();
    }

    /**
     * Validates a registration request ensuring all required fields are present and non-empty.
     *
     * @param request the registration request to validate
     * @throws IllegalArgumentException if any required field is missing or invalid
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getPassword(), "Password is required");
        validateRequired(request.getEmail(), "Email is required");
        validateRequired(request.getFullName(), "Full name is required");
        validateRequired(request.getStreetAddress(), "Street address is required");
        validateRequired(request.getCity(), "City is required");
        validateRequired(request.getState(), "State is required");
        validateRequired(request.getZipCode(), "ZIP code is required");
        validateRequired(request.getCountry(), "Country is required");
        validateRequired(request.getSsn(), "SSN is required");
        validateRequired(request.getPhoneNumber(), "Phone number is required");
        validateRequired(request.getInvestmentExperience(), "Investment experience is required");
        if (request.getInitialDeposit() == null) {
            throw new IllegalArgumentException("Initial deposit amount is required");
        }
        if (request.getDateOfBirth() == null) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new IllegalArgumentException("Terms and conditions must be accepted");
        }
    }

    /**
     * Validates a login request ensuring all required fields are present and non-empty.
     *
     * @param request the login request to validate
     * @throws IllegalArgumentException if any required field is missing or invalid
     */
    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getPassword(), "Password is required");
    }

    /**
     * Validates that a string value is not null or blank.
     *
     * @param value the string value to validate
     * @param message the error message to throw if validation fails
     * @throws IllegalArgumentException if the value is null or blank
     */
    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Response object for authentication operations containing username and message.
     */
    public static class AuthResponse {
        private final String username;
        private final String message;

        public AuthResponse(String username, String message) {
            this.username = username;
            this.message = message;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Result object for successful login containing username, message, and JWT token.
     * The token is not serialized directly in the response; it is only used by the controller
     * to set the authentication cookie.
     */
    public static class LoginResult {
        private final String username;
        private final String message;
        private final String token;

        public LoginResult(String username, String message, String token) {
            this.username = username;
            this.message = message;
            this.token = token;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }

        public String getToken() {
            return token;
        }
    }
}
