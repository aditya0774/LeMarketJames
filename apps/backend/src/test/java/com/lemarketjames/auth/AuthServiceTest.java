package com.lemarketjames.auth;

import com.lemarketjames.auth.domain.AccountEntity;
import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.auth.domain.AddressEntity;
import com.lemarketjames.auth.domain.AddressRepository;
import com.lemarketjames.auth.domain.ClientEntity;
import com.lemarketjames.auth.domain.ClientRepository;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtService;
import com.lemarketjames.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String JWT_KEY = "unit-test-signing-key-please-32bytes-minimum";

    private AuthService authService;
    private ClientRepository clientRepository;
    private AddressRepository addressRepository;
    private AccountRepository accountRepository;
    // Stands in for the clients table: keyed by email, as login looks clients up by email.
    private Map<String, ClientEntity> clientsByEmail;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        addressRepository = mock(AddressRepository.class);
        accountRepository = mock(AccountRepository.class);
        clientsByEmail = new ConcurrentHashMap<>();

        when(clientRepository.existsByUsername(anyString())).thenAnswer(invocation ->
                clientsByEmail.values().stream().anyMatch(c -> c.getUsername().equals(invocation.getArgument(0))));
        when(clientRepository.existsByEmail(anyString())).thenAnswer(invocation ->
                clientsByEmail.containsKey(invocation.<String>getArgument(0)));
        when(clientRepository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(clientsByEmail.get(invocation.<String>getArgument(0))));
        when(clientRepository.save(any(ClientEntity.class))).thenAnswer(invocation -> {
            ClientEntity client = invocation.getArgument(0);
            clientsByEmail.put(client.getEmail(), client);
            return client;
        });
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService = newAuthService();
    }

    private AuthService newAuthService() {
        return new AuthService(
                new JwtService(JWT_KEY, 3600000),
                clientRepository,
                addressRepository,
                accountRepository,
                200
        );
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

        assertThrows(ValidationException.class, () -> authService.register(request));
    }

    // The initial deposit amount is mandatory, so a null value should be rejected.
    @Test
    void registerRejectsMissingInitialDeposit() {
        RegisterRequest request = validRegisterRequest("bob");
        request.setInitialDeposit(null);

        assertThrows(ValidationException.class, () -> authService.register(request));
    }

    // Two accounts cannot share the same email address, even under different usernames.
    @Test
    void registerRejectsDuplicateEmail() {
        authService.register(validRegisterRequest("alice"));

        RegisterRequest request = validRegisterRequest("alice2");
        request.setEmail("alice@example.com");

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    // After registering, logging in with the email and password should succeed and return the username and a token.
    @Test
    void loginSucceedsWithCorrectCredentials() {
        authService.register(validRegisterRequest("alice"));

        AuthService.LoginResult result = authService.login(new LoginRequest("alice@example.com", "Pass123!"));

        assertEquals("alice", result.getUsername());
        assertEquals("Login successful", result.getMessage());
        assertNotNull(result.getToken());
        assertNotNull(clientsByEmail.get("alice@example.com").getLastLogin());
    }

    // Login must read from the database, so a client saved earlier (e.g. before a restart) can still log in.
    @Test
    void loginSucceedsForClientStoredBeforeRestart() {
        authService.register(validRegisterRequest("alice"));
        AuthService restartedService = newAuthService();

        AuthService.LoginResult result = restartedService.login(new LoginRequest("alice@example.com", "Pass123!"));

        assertEquals("alice", result.getUsername());
    }

    // Emails are stored lowercased, so login should match regardless of letter case or surrounding spaces.
    @Test
    void loginIgnoresEmailCase() {
        authService.register(validRegisterRequest("alice"));

        AuthService.LoginResult result = authService.login(new LoginRequest("  Alice@Example.COM ", "Pass123!"));

        assertEquals("alice", result.getUsername());
    }

    // Logging in with an email that was never registered should fail.
    @Test
    void loginRejectsUnknownEmail() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "Pass123!")));
        assertEquals("Invalid email or password", exception.getMessage());
    }

    // Logging in with the wrong password for a real email should fail.
    @Test
    void loginRejectsIncorrectPassword() {
        authService.register(validRegisterRequest("alice"));

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
    }

    // Encoding a password should hash it, and the hash should still verify against the original password.
    @Test
    void passwordIsHashedAndVerifiable() {
        String encoded = authService.encodePassword("Pass123!");

        assertEquals(true, authService.matchesPassword("Pass123!", encoded));
    }

    // Three consecutive wrong-password attempts must lock the account, even for the correct password afterwards.
    @Test
    void loginLocksAccountAfterThreeFailedAttempts() {
        authService.register(validRegisterRequest("alice"));

        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        }

        IllegalArgumentException lockedException = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "Pass123!")));
        assertTrue(lockedException.getMessage().contains("locked"));
    }

    // Once the lockout duration has passed, the account should accept correct credentials again.
    @Test
    void loginSucceedsAfterLockoutExpires() throws InterruptedException {
        authService.register(validRegisterRequest("alice"));

        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        }

        Thread.sleep(250);

        AuthService.LoginResult result = authService.login(new LoginRequest("alice@example.com", "Pass123!"));
        assertEquals("alice", result.getUsername());
    }

    private RegisterRequest validRegisterRequest(String username) {
        RegisterRequest request = new RegisterRequest(username, "Pass123!", username + "@example.com", "Test User");
        request.setStreetAddress("123 Main St");
        request.setCity("Springfield");
        request.setState("IL");
        request.setZipCode("62701");
        request.setCountry("US");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(BigDecimal.valueOf(500));
        request.setInvestmentExperience("beginner");
        request.setEmploymentStatus("employed");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        return request;
    }
}
