package com.lemarketjames.auth.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Service class for JWT token generation and validation.
 * Handles creating tokens with user information and verifying token validity.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Constructs a JwtService with the provided secret key and expiration time.
     *
     * @param secret the secret key string for signing tokens
     * @param expirationMs the token expiration time in milliseconds
     */
    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a JWT token for the given username.
     * The token is signed with the secret key and includes an expiration time.
     *
     * @param username the username to include in the token subject
     * @return a signed JWT token string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Gets the token expiration time in seconds.
     *
     * @return the expiration time in seconds
     */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    /**
     * Extracts the username from a JWT token if the token is valid and unexpired.
     *
     * @param token the JWT token to parse
     * @return an Optional containing the username if valid, or empty if the token is invalid or expired
     */
    public java.util.Optional<String> extractUsername(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return java.util.Optional.ofNullable(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
