package com.lemarketjames.sessions.service;

import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Session Service Unit Tests")
class SessionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private SessionService sessionService;
    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-256-bits";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(TEST_SECRET, accountRepository);
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== Valid Session Tests ==========

    @Test
    @DisplayName("validateSession returns SessionDto for valid, non-expired token")
    void testValidateSessionSuccess() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(1, "user1")).thenReturn(true);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000); // 1 hour from now
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        SessionDto result = sessionService.validateSession(1, token);

        assertNotNull(result);
        assertEquals(1, result.getAccountId());
        assertTrue(result.getIsActive());
        assertNotNull(result.getExpiresAt());
    }

    @Test
    @DisplayName("validateSession preserves expiration timestamp correctly")
    void testValidateSessionPreservesExpirationTime() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(1, "user1")).thenReturn(true);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        SessionDto result = sessionService.validateSession(1, token);

        LocalDateTime expectedExpiry = expiresAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        assertNotNull(result.getExpiresAt());
        assertEquals(expectedExpiry.getYear(), result.getExpiresAt().getYear());
        assertEquals(expectedExpiry.getMonthValue(), result.getExpiresAt().getMonthValue());
    }

    @Test
    @DisplayName("validateSession succeeds when token has no expiration")
    void testValidateSessionWithNoExpiration() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(1, "user1")).thenReturn(true);

        String token = Jwts.builder()
                .subject("user1")
                .signWith(signingKey)
                .compact();

        SessionDto result = sessionService.validateSession(1, token);

        assertNotNull(result);
        assertTrue(result.getIsActive());
        assertNull(result.getExpiresAt());
    }

    // ========== Expired Token Tests ==========

    @Test
    @DisplayName("validateSession throws SessionExpiredException for expired token")
    void testValidateSessionThrowsForExpiredToken() {
        setupAuthentication("user1");
        // Don't stub repository - JWT parser validates expiration and throws before repo call

        Date expiresAt = new Date(System.currentTimeMillis() - 3600000); // 1 hour ago
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        // JWT parser automatically validates expiration, so we get generic error message
        SessionExpiredException ex = assertThrows(SessionExpiredException.class,
                () -> sessionService.validateSession(1, token));
        assertTrue(ex.getMessage().contains("Invalid or expired session token"));
    }

    @Test
    @DisplayName("validateSession throws for token expiring just now")
    void testValidateSessionThrowsForTokenExpiringNow() {
        setupAuthentication("user1");
        // Don't stub repository - JWT parser validates expiration

        Date expiresAt = new Date(System.currentTimeMillis() - 1); // Just expired
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        // JWT parser validates expiration, so we get generic error message
        assertThrows(SessionExpiredException.class, () -> sessionService.validateSession(1, token));
    }

    // ========== Invalid Token Format Tests ==========

    @Test
    @DisplayName("validateSession throws SessionExpiredException for malformed token")
    void testValidateSessionThrowsForMalformedToken() {
        setupAuthentication("user1");
        // Don't stub repository - malformed token fails during JWT parsing

        String malformedToken = "invalid.token.here";

        // Malformed token is caught during JWT parsing
        assertThrows(SessionExpiredException.class, () -> sessionService.validateSession(1, malformedToken));
    }

    @Test
    @DisplayName("validateSession throws for token with invalid signature")
    void testValidateSessionThrowsForTamperedSignature() {
        setupAuthentication("user1");
        // Don't stub repository - signature verification fails during JWT parsing

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        String tamperedToken = token.substring(0, token.length() - 1) + "X";

        // Signature verification fails during parsing
        assertThrows(SessionExpiredException.class, () -> sessionService.validateSession(1, tamperedToken));
    }

    @Test
    @DisplayName("validateSession throws for empty token string")
    void testValidateSessionThrowsForEmptyToken() {
        setupAuthentication("user1");
        // Don't stub repository - empty token fails during JWT parsing

        // Empty token fails during JWT parsing
        assertThrows(SessionExpiredException.class, () -> sessionService.validateSession(1, ""));
    }

    // ========== Authentication Context Tests ==========

    @Test
    @DisplayName("validateSession throws AccessDeniedException when authentication is null")
    void testValidateSessionThrowsWhenAuthenticationNull() {
        SecurityContextHolder.clearContext();

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> sessionService.validateSession(1, token));
        assertEquals("Account access is not allowed", ex.getMessage());
    }

    @Test
    @DisplayName("validateSession throws AccessDeniedException when authentication is not authenticated")
    void testValidateSessionThrowsWhenNotAuthenticated() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user1", "n/a");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> sessionService.validateSession(1, token));
        assertEquals("Account access is not allowed", ex.getMessage());
    }

    @Test
    @DisplayName("validateSession throws AccessDeniedException when username mismatch between token and auth")
    void testValidateSessionThrowsWhenUsernameMismatch() {
        setupAuthentication("user1");
        // Don't stub the repository - exception is thrown before repository call

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user2")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> sessionService.validateSession(1, token));
        assertEquals("Account access is not allowed", ex.getMessage());
    }

    @Test
    @DisplayName("validateSession throws AccessDeniedException when account/username not in database")
    void testValidateSessionThrowsWhenAccountNotFound() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(1, "user1")).thenReturn(false);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> sessionService.validateSession(1, token));
        assertEquals("Account access is not allowed", ex.getMessage());
    }

    @Test
    @DisplayName("validateSession uses account ID from database, not from token")
    void testValidateSessionUsesCorrectAccountId() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(42, "user1")).thenReturn(true);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        SessionDto result = sessionService.validateSession(42, token);

        assertEquals(42, result.getAccountId());
        assertEquals(42, result.getSessionId());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("validateSession with token for different user throws AccessDeniedException")
    void testValidateSessionThrowsForDifferentUser() {
        setupAuthentication("alice");
        // Don't stub repository - auth check happens first

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("bob")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> sessionService.validateSession(1, token));
        assertEquals("Account access is not allowed", ex.getMessage());
    }

    @Test
    @DisplayName("validateSession maintains session ID equal to account ID")
    void testValidateSessionSessionIdEqualsAccountId() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(99, "user1")).thenReturn(true);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        SessionDto result = sessionService.validateSession(99, token);

        assertEquals(result.getSessionId(), result.getAccountId());
    }

    @Test
    @DisplayName("validateSession always returns isActive=true on success")
    void testValidateSessionAlwaysActiveOnSuccess() {
        setupAuthentication("user1");
        when(accountRepository.existsByAccountIdAndUsername(1, "user1")).thenReturn(true);

        Date expiresAt = new Date(System.currentTimeMillis() + 3600000);
        String token = Jwts.builder()
                .subject("user1")
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();

        SessionDto result = sessionService.validateSession(1, token);

        assertTrue(result.getIsActive());
    }

    // ========== Helper Methods ==========

    private void setupAuthentication(String username) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(username, "n/a", "ROLE_USER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

