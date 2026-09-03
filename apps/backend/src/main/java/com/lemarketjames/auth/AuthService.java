package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final Map<String, String> userStore = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(JwtService jwtService) {
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername();
        String password = request.getPassword();

        if (userStore.containsKey(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }

        userStore.put(username, encodePassword(password));
        return new AuthResponse(username, "User registered successfully");
    }

    public LoginResult login(LoginRequest request) {
        validateLoginRequest(request);

        String username = request.getUsername();
        String password = request.getPassword();
        String storedPassword = userStore.get(username);

        if (storedPassword == null || !matchesPassword(password, storedPassword)) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtService.generateToken(username);
        return new LoginResult(username, "Login successful", token);
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public long getTokenExpirySeconds() {
        return jwtService.getExpirationSeconds();
    }

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
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getPassword(), "Password is required");
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

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

    /** Not serialized directly; the token is only used by the controller to set the auth cookie. */
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
