package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(new JwtService("unit-test-signing-key-please-32bytes-minimum", 3600000));
    }

    // A fully filled-out registration should succeed and return the new username.
    @Test
    void registerSucceedsWithValidRequest() {
        AuthService.AuthResponse response = authService.register(validRegisterRequest("alice"));

        assertEquals("alice", response.getUsername());
        assertEquals("User registered successfully", response.getMessage());
    }

    // Registering the same username twice must fail on the second attempt.
    @Test
    void registerRejectsDuplicateUsername() {
        authService.register(validRegisterRequest("alice"));

        assertThrows(IllegalArgumentException.class, () -> authService.register(validRegisterRequest("alice")));
    }

    // A missing mandatory field (email) should be rejected before an account is created.
    @Test
    void registerRejectsMissingRequiredField() {
        RegisterRequest request = validRegisterRequest("bob");
        request.setEmail(null);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    // The initial deposit amount is mandatory, so a null value should be rejected.
    @Test
    void registerRejectsMissingInitialDeposit() {
        RegisterRequest request = validRegisterRequest("bob");
        request.setInitialDeposit(null);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    // Registration must be refused unless the client explicitly accepts the terms and conditions.
    @Test
    void registerRejectsWhenTermsNotAccepted() {
        RegisterRequest request = validRegisterRequest("bob");
        request.setTermsAccepted(false);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    // Two accounts cannot share the same email address, even under different usernames.
    @Test
    void registerRejectsDuplicateEmail() {
        authService.register(validRegisterRequest("alice"));

        RegisterRequest request = validRegisterRequest("alice2");
        request.setEmail("alice@example.com");

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    // After registering, logging in with the same username/password should succeed and return a token.
    @Test
    void loginSucceedsWithCorrectCredentials() {
        authService.register(validRegisterRequest("alice"));

        AuthService.LoginResult result = authService.login(new LoginRequest("alice", "Pass123!"));

        assertEquals("alice", result.getUsername());
        assertEquals("Login successful", result.getMessage());
        assertNotNull(result.getToken());
    }

    // Logging in as a username that was never registered should fail.
    @Test
    void loginRejectsUnknownUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("nobody", "Pass123!")));
    }

    // Logging in with the wrong password for a real username should fail.
    @Test
    void loginRejectsIncorrectPassword() {
        authService.register(validRegisterRequest("alice"));

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice", "WrongPass!")));
    }

    // Encoding a password should hash it, and the hash should still verify against the original password.
    @Test
    void passwordIsHashedAndVerifiable() {
        String encoded = authService.encodePassword("Pass123!");

        assertEquals(true, authService.matchesPassword("Pass123!", encoded));
    }

    private RegisterRequest validRegisterRequest(String username) {
        RegisterRequest request = new RegisterRequest(username, "Pass123!", username + "@example.com", "Test User");
        request.setStreetAddress("123 Main St");
        request.setCity("Springfield");
        request.setState("IL");
        request.setZipCode("62701");
        request.setCountry("USA");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(BigDecimal.valueOf(500));
        request.setInvestmentExperience("beginner");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        request.setTermsAccepted(true);
        return request;
    }
}
