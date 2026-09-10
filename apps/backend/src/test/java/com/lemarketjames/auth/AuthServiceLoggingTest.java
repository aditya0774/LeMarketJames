package com.lemarketjames.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lemarketjames.auth.domain.AccountEntity;
import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.auth.domain.AddressEntity;
import com.lemarketjames.auth.domain.AddressRepository;
import com.lemarketjames.auth.domain.ClientEntity;
import com.lemarketjames.auth.domain.ClientRepository;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceLoggingTest {

    private AuthService authService;
    private Logger authServiceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        AddressRepository addressRepository = mock(AddressRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);

        when(clientRepository.existsByUsername(anyString())).thenReturn(false);
        when(clientRepository.existsByEmail(anyString())).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService = new AuthService(
                new JwtService("unit-test-signing-key-please-32bytes-minimum", 3600000),
                clientRepository,
                addressRepository,
                accountRepository,
                30000
        );

        authServiceLogger = (Logger) LoggerFactory.getLogger(AuthService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        authServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        authServiceLogger.detachAppender(logAppender);
    }

    // A successful login must produce an audit log entry naming the user.
    @Test
    void successfulLoginIsLogged() {
        authService.register(validRegisterRequest("alice"));
        logAppender.list.clear();

        authService.login(new LoginRequest("alice", "Pass123!"));

        assertTrue(logContains("Login succeeded"), "expected a login-succeeded log entry");
    }

    // A failed login attempt must produce an audit log entry, not just a thrown exception.
    @Test
    void failedLoginIsLogged() {
        authService.register(validRegisterRequest("alice"));
        logAppender.list.clear();

        try {
            authService.login(new LoginRequest("alice", "WrongPass!"));
        } catch (IllegalArgumentException expected) {
            // handled by the assertion below; the login itself is expected to fail
        }

        assertTrue(logContains("Login failed"), "expected a login-failed log entry");
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
