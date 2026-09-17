package com.lemarketjames.sessions.service;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Session validation using JWT token claims.
 */
@Service
public class SessionService {

  private final SecretKey signingKey;

  public SessionService(@Value("${jwt.secret}") String secret) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * AC1: Validate session (JWT token) is not expired.
   * Parses JWT claims and verifies token signature and expiration time.
   * 
   * @param accountId the account ID associated with the token
   * @param token the JWT token string to validate
   * @return SessionDto containing session details if valid
   * @throws SessionExpiredException if token is expired, invalid, or signature verification fails
   */
  public SessionDto validateSession(Integer accountId, String token) {
    try {
      // Parse and validate JWT token
      var claims = Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .getPayload();

      // Check expiration
      Date expiresAt = claims.getExpiration();
      if (expiresAt != null && expiresAt.before(new Date())) {
        throw new SessionExpiredException("Session token has expired");
      }

      LocalDateTime expiresAtLocal = expiresAt != null 
          ? expiresAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
          : null;

      return new SessionDto(
          accountId,
          accountId,
          expiresAtLocal,
          true
      );
    } catch (SessionExpiredException e) {
      throw e;
    } catch (Exception e) {
      throw new SessionExpiredException("Invalid or expired session token: " + e.getMessage());
    }
  }
}

