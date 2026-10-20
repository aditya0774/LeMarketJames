package com.lemarketjames.auth;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.StaffUserEntity;
import com.lemarketjames.common.domain.StaffUserRepository;
import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.security.AccountInactiveException;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.common.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String JWT_KEY = "unit-test-signing-key-please-32bytes-minimum";

    private AuthService authService;
    private ClientRepository clientRepository;
    private AddressRepository addressRepository;
    private AccountRepository accountRepository;
    private StaffUserRepository staffUserRepository;
    private final MutableClock clock = new MutableClock();
    // Stand in for the clients and staff_users tables: keyed by email, as login looks both up by email.
    private Map<String, ClientEntity> clientsByEmail;
    private Map<String, StaffUserEntity> staffByEmail;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        addressRepository = mock(AddressRepository.class);
        accountRepository = mock(AccountRepository.class);
        staffUserRepository = mock(StaffUserRepository.class);
        clientsByEmail = new ConcurrentHashMap<>();
        staffByEmail = new ConcurrentHashMap<>();
        when(staffUserRepository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(staffByEmail.get(invocation.<String>getArgument(0))));
        when(staffUserRepository.findByUsername(anyString())).thenAnswer(invocation ->
            staffByEmail.values().stream()
                .filter(staff -> staff.getUsername().equals(invocation.<String>getArgument(0)))
                .findFirst());
        when(staffUserRepository.save(any(StaffUserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(clientRepository.existsByUsername(anyString())).thenAnswer(invocation ->
                clientsByEmail.values().stream().anyMatch(c -> c.getUsername().equals(invocation.getArgument(0))));
        when(clientRepository.existsByEmail(anyString())).thenAnswer(invocation ->
                clientsByEmail.containsKey(invocation.<String>getArgument(0)));
        when(clientRepository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(clientsByEmail.get(invocation.<String>getArgument(0))));
        when(clientRepository.findByUsername(anyString())).thenAnswer(invocation ->
            clientsByEmail.values().stream()
                .filter(client -> client.getUsername().equals(invocation.<String>getArgument(0)))
                .findFirst());
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
        PlatformSettings settings = new PlatformSettings();
        settings.getAuth().getLockout().setMaxAttempts(3);
        // Keep default lockout long enough to be deterministic on slower CI agents.
        settings.getAuth().getLockout().setDuration(Duration.ofSeconds(5));
        return newAuthService(settings);
    }

    // Every service in a test shares one clock, as restarted instances share real time.
    private AuthService newAuthService(PlatformSettings settings) {
        return new AuthService(
                new JwtService(JWT_KEY, 3600000),
                clientRepository,
                addressRepository,
                accountRepository,
                staffUserRepository,
                settings,
                clock
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

        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        }

        IllegalArgumentException lockedException = assertThrows(IllegalArgumentException.class,
            () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        assertEquals("Account locked for 1 minute.", lockedException.getMessage());

        IllegalArgumentException blockedException = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "Pass123!")));
        assertEquals("Account temporarily locked. Try again in 1 minute.", blockedException.getMessage());
    }

        @Test
        void successfulLoginResetsFailedAttemptCount() {
        authService.register(validRegisterRequest("alice"));

        assertThrows(IllegalArgumentException.class,
            () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        assertThrows(IllegalArgumentException.class,
            () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));

        assertEquals("alice", authService.login(new LoginRequest("alice@example.com", "Pass123!")).getUsername());
        assertEquals(0, clientsByEmail.get("alice@example.com").getFailedLoginAttempts());

        assertThrows(IllegalArgumentException.class,
            () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        assertThrows(IllegalArgumentException.class,
            () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        assertEquals(2, clientsByEmail.get("alice@example.com").getFailedLoginAttempts());
        }

    // Once the lockout duration has passed, the account should accept correct credentials again.
    @Test
    void loginSucceedsAfterLockoutExpires() throws InterruptedException {
        PlatformSettings shortLockout = new PlatformSettings();
        shortLockout.getAuth().getLockout().setMaxAttempts(3);
        shortLockout.getAuth().getLockout().setDuration(Duration.ofMillis(200));
        AuthService service = newAuthService(shortLockout);

        service.register(validRegisterRequest("alice"));

        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.login(new LoginRequest("alice@example.com", "WrongPass!")));
        }

        clock.advance(shortLockout.getAuth().getLockout().getDuration().plusMillis(1));

        AuthService.LoginResult result = service.login(new LoginRequest("alice@example.com", "Pass123!"));
        assertEquals("alice", result.getUsername());
    }

    // The lock is stored on the client, so a restarted auth-service still refuses the login.
    @Test
    void lockoutSurvivesRestart() {
        authService.register(validRegisterRequest("alice"));
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> authService.login(new LoginRequest("alice@example.com", "WrongPass!")));
        }

        AuthService restartedService = newAuthService();

        IllegalArgumentException locked = assertThrows(IllegalArgumentException.class,
                () -> restartedService.login(new LoginRequest("alice@example.com", "Pass123!")));
        assertTrue(locked.getMessage().contains("locked"));
    }

    // The attempt limit comes from lmj.auth.lockout.max-attempts rather than being hard-coded.
    @Test
    void lockoutUsesConfiguredAttemptLimit() {
        PlatformSettings settings = new PlatformSettings();
        settings.getAuth().getLockout().setMaxAttempts(5);
        AuthService lenient = newAuthService(settings);
        lenient.register(validRegisterRequest("alice"));

        for (int i = 0; i < 4; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> lenient.login(new LoginRequest("alice@example.com", "WrongPass!")));
        }

        assertEquals("alice", lenient.login(new LoginRequest("alice@example.com", "Pass123!")).getUsername());
    }

    // Clients get the CLIENT role in their token.
    @Test
    void clientLoginCarriesClientRole() {
        authService.register(validRegisterRequest("alice"));

        AuthService.LoginResult result = authService.login(new LoginRequest("alice@example.com", "Pass123!"));

        assertEquals(Set.of(Role.CLIENT), result.getRoles());
        assertEquals(Set.of(Role.CLIENT), new JwtService(JWT_KEY, 3600000).extractClaims(result.getToken()).orElseThrow().roles());
    }

    @Test
    void logoutInvalidatesAClientToken() {
        authService.register(validRegisterRequest("alice"));
        String token = authService.login(new LoginRequest("alice@example.com", "Pass123!")).getToken();

        authService.logout(token);

        assertEquals(clock.instant(), clientsByEmail.get("alice@example.com").getSessionInvalidatedAt());
        verify(clientRepository, org.mockito.Mockito.atLeastOnce()).save(clientsByEmail.get("alice@example.com"));
    }

    @Test
    void logoutInvalidatesAStaffToken() {
        StaffUserEntity ops = new StaffUserEntity();
        ops.setUsername("olivia_ops");
        ops.setEmail("ops@example.com");
        ops.setPassword(authService.encodePassword("Pass123!"));
        ops.setFullName("Olivia Ops");
        ops.setRole(Role.TRADING_OPS);
        staffByEmail.put(ops.getEmail(), ops);
        String token = authService.login(new LoginRequest("ops@example.com", "Pass123!")).getToken();

        authService.logout(token);

        assertEquals(clock.instant(), ops.getSessionInvalidatedAt());
        verify(staffUserRepository, org.mockito.Mockito.atLeastOnce()).save(ops);
    }

    @Test
    void logoutIgnoresAnInvalidToken() {
        authService.logout("not-a-valid-token");

        verify(clientRepository, org.mockito.Mockito.never()).save(any(ClientEntity.class));
        verify(staffUserRepository, org.mockito.Mockito.never()).save(any(StaffUserEntity.class));
    }

    // Staff log in through the same endpoint and get their staff role instead of CLIENT.
    @Test
    void staffLoginCarriesStaffRole() {
        StaffUserEntity ops = new StaffUserEntity();
        ops.setUsername("olivia_ops");
        ops.setEmail("ops@example.com");
        ops.setPassword(authService.encodePassword("Pass123!"));
        ops.setFullName("Olivia Ops");
        ops.setRole(Role.TRADING_OPS);
        staffByEmail.put(ops.getEmail(), ops);

        AuthService.LoginResult result = authService.login(new LoginRequest("ops@example.com", "Pass123!"));

        assertEquals("olivia_ops", result.getUsername());
        assertEquals(Set.of(Role.TRADING_OPS), result.getRoles());
    }

    // LMKT-57 AC1: a client that is no longer ACTIVE knows their password but is refused with ACCOUNT_INACTIVE.
    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"CLOSED", "SUSPENDED", "EXPIRED"})
    void inactiveClientIsRefusedAfterACorrectPassword(AccountStatus status) {
        authService.register(validRegisterRequest("carl"));
        clientsByEmail.get("carl@example.com").setAccountStatus(status);

        LoginRequest request = new LoginRequest("carl@example.com", "Pass123!");

        AccountInactiveException refused = assertThrows(AccountInactiveException.class, () -> authService.login(request));
        assertEquals("This account is no longer active. Please contact the firm.", refused.getMessage());
    }

    // Staff follow their active flag: a deactivated staff login is refused the same way.
    @Test
    void deactivatedStaffIsRefusedAfterACorrectPassword() {
        StaffUserEntity ops = new StaffUserEntity();
        ops.setUsername("olivia_ops");
        ops.setEmail("ops@example.com");
        ops.setPassword(authService.encodePassword("Pass123!"));
        ops.setFullName("Olivia Ops");
        ops.setRole(Role.TRADING_OPS);
        ops.setActive(false);
        staffByEmail.put(ops.getEmail(), ops);

        LoginRequest request = new LoginRequest("ops@example.com", "Pass123!");

        assertThrows(AccountInactiveException.class, () -> authService.login(request));
    }

    // The status is only checked after the password, so a wrong guess never reveals that an account is closed.
    @Test
    void wrongPasswordForAClosedAccountStillSaysInvalidCredentials() {
        authService.register(validRegisterRequest("carl"));
        clientsByEmail.get("carl@example.com").setAccountStatus(AccountStatus.CLOSED);

        LoginRequest request = new LoginRequest("carl@example.com", "WrongPass!");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> authService.login(request));
        assertEquals("Invalid email or password", refused.getMessage());
    }

    // A deleted account has no password to check, so it gets the same reply as an unknown email.
    @Test
    void missingAccountGetsTheGenericInvalidCredentialsMessage() {
        LoginRequest request = new LoginRequest("gone@example.com", "Pass123!");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> authService.login(request));
        assertEquals("Invalid email or password", refused.getMessage());
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
