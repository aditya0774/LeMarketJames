package com.lemarketjames.sessions.service;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionServiceTest {
  private SessionService service;
  private final String testSecret = "test-secret-key-that-is-long-enough-for-256-bits";
  private SecretKey signingKey;

  @BeforeEach
  void setUp() {
    var accounts = org.mockito.Mockito.mock(com.lemarketjames.auth.domain.AccountRepository.class);
    org.mockito.Mockito.when(accounts.existsByAccountIdAndUsername(1, "user1")).thenReturn(true);
    service = new SessionService(testSecret, accounts);
    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
        new org.springframework.security.authentication.TestingAuthenticationToken("user1", "n/a", "ROLE_USER"));
    signingKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
  }

  @org.junit.jupiter.api.AfterEach
  void clearAuthentication() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  /**
   * AC1: Session validation succeeds for valid, non-expired JWT token
   */
  @Test
  void validateSessionSucceedsForValidToken() {
    // Create a valid token that expires in the future
    Date expiresAt = new Date(System.currentTimeMillis() + 3600000); // 1 hour from now
    String token = Jwts.builder()
        .subject("user1")
        .expiration(expiresAt)
        .signWith(signingKey)
        .compact();

    SessionDto result = service.validateSession(1, token);

    assertNotNull(result);
    assertTrue(result.getIsActive());
  }

  /**
   * AC1: Session validation fails for expired JWT token
   */
  @Test
  void validateSessionThrowsForExpiredToken() {
    // Create an expired token
    Date expiresAt = new Date(System.currentTimeMillis() - 3600000); // 1 hour ago
    String token = Jwts.builder()
        .subject("user1")
        .expiration(expiresAt)
        .signWith(signingKey)
        .compact();

    assertThrows(SessionExpiredException.class, () -> {
      service.validateSession(1, token);
    });
  }

  /**
   * AC1: Session validation fails for invalid token
   */
  @Test
  void validateSessionThrowsForInvalidToken() {
    String invalidToken = "invalid.token.here";

    assertThrows(SessionExpiredException.class, () -> {
      service.validateSession(1, invalidToken);
    });
  }
}

