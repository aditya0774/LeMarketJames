package com.lemarketjames.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.StaffUserRepository;
import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.security.AccountInactiveException;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.SecurityEventLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceLoggingTest {

    private AuthService authService;
    private Map<String, ClientEntity> clientsByEmail;
    private Logger authServiceLogger;
    // Security events (ACCESS_REVOKED) are written by the shared SecurityEventLog, on its own logger.
    private Logger securityEventLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        AddressRepository addressRepository = mock(AddressRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);

        clientsByEmail = new ConcurrentHashMap<>();
        when(clientRepository.existsByUsername(anyString())).thenReturn(false);
        when(clientRepository.existsByEmail(anyString())).thenReturn(false);
        when(clientRepository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(clientsByEmail.get(invocation.<String>getArgument(0))));
        when(clientRepository.save(any(ClientEntity.class))).thenAnswer(invocation -> {
            ClientEntity client = invocation.getArgument(0);
            clientsByEmail.put(client.getEmail(), client);
            return client;
        });
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService = new AuthService(
                new JwtService("unit-test-signing-key-please-32bytes-minimum", 3600000),
                clientRepository,
                addressRepository,
                accountRepository,
                mock(StaffUserRepository.class),
                new PlatformSettings()
        );

        authServiceLogger = (Logger) LoggerFactory.getLogger(AuthService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        authServiceLogger.addAppender(logAppender);
        securityEventLogger = (Logger) LoggerFactory.getLogger(SecurityEventLog.class);
        securityEventLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        authServiceLogger.detachAppender(logAppender);
        securityEventLogger.detachAppender(logAppender);
    }

    // A successful login must produce an audit log entry naming the user.
    @Test
    void successfulLoginIsLogged() {
        authService.register(validRegisterRequest("alice"));
        logAppender.list.clear();

        authService.login(new LoginRequest("alice@example.com", "Pass123!"));

        assertTrue(logContains("Login succeeded"), "expected a login-succeeded log entry");
    }

    // A failed login attempt must produce an audit log entry, not just a thrown exception.
    @Test
    void failedLoginIsLogged() {
        authService.register(validRegisterRequest("alice"));
        logAppender.list.clear();

        try {
            authService.login(new LoginRequest("alice@example.com", "WrongPass!"));
        } catch (IllegalArgumentException expected) {
            // handled by the assertion below; the login itself is expected to fail
        }

        assertTrue(logContains("Login failed"), "expected a login-failed log entry");
    }

    @Test
    void accountLockIsLoggedAsSecurityEvent() {
        authService.register(validRegisterRequest("alice"));
        logAppender.list.clear();

        for (int i = 0; i < 3; i++) {
            try {
                authService.login(new LoginRequest("alice@example.com", "WrongPass!"));
            } catch (IllegalArgumentException expected) {
                // The lock transition is asserted through the captured security event below.
            }
        }

        assertTrue(logContains("ACCOUNT_LOCKED"), "expected an account-locked security event");
        assertTrue(logContains("15 minutes"), "expected the security event to include the duration");
    }

    // LMKT-57 AC3: a refused sign-in is a security event naming the account, the reason and a UTC time.
    @Test
    void refusedSignInIsLoggedAsAccessRevoked() {
        authService.register(validRegisterRequest("carl"));
        clientsByEmail.get("carl@example.com").setAccountStatus(AccountStatus.CLOSED);
        logAppender.list.clear();
        LoginRequest request = new LoginRequest("carl@example.com", "Pass123!");

        assertThrows(AccountInactiveException.class, () -> authService.login(request));

        String event = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("ACCESS_REVOKED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an ACCESS_REVOKED security event"));
        Matcher fields = Pattern.compile("ACCESS_REVOKED username=(\\S+) reason=(\\S+) at=(\\S+)").matcher(event);
        assertTrue(fields.matches(), event);
        assertEquals("carl", fields.group(1));
        assertEquals("CLOSED", fields.group(2));
        assertTrue(fields.group(3).endsWith("Z"), "timestamp must be UTC: " + fields.group(3));
        Instant.parse(fields.group(3));
        assertFalse(event.contains("Pass123!"), "the password must never be logged");
    }

    // A wrong password is not a revocation: it must not reveal (even in the log) that the account is closed.
    @Test
    void wrongPasswordForAClosedAccountIsNotLoggedAsAccessRevoked() {
        authService.register(validRegisterRequest("carl"));
        clientsByEmail.get("carl@example.com").setAccountStatus(AccountStatus.CLOSED);
        logAppender.list.clear();
        LoginRequest request = new LoginRequest("carl@example.com", "WrongPass!");

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));

        assertFalse(logContains("ACCESS_REVOKED"), "a wrong password must not log ACCESS_REVOKED");
    }

    private boolean logContains(String expectedSubstring) {
        return logAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(expectedSubstring));
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
