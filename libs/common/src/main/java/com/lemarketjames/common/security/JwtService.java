package com.lemarketjames.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service class for JWT token generation and validation.
 * Handles creating tokens with user information and verifying token validity.
 */
@Service
public class JwtService {

    /** The claim holding the token holder's {@link Role} names. */
    public static final String ROLES_CLAIM = "roles";

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
     * Generates a JWT token for a client (the {@link Role#CLIENT} role).
     *
     * @param username the username to include in the token subject
     * @return a signed JWT token string
     */
    public String generateToken(String username) {
        return generateToken(username, Set.of(Role.CLIENT));
    }

    /**
     * Generates a JWT token carrying the holder's roles, so every service can authorize the
     * request from the token alone (contract C7).
     * The token is signed with the secret key and includes an expiration time.
     *
     * @param username the username to include in the token subject
     * @param roles    the holder's roles, stored in the {@value #ROLES_CLAIM} claim
     * @return a signed JWT token string
     */
    public String generateToken(String username, Set<Role> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, roles.stream().map(Role::name).sorted().toList())
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
        return extractClaims(token).map(TokenClaims::username);
    }

    /**
     * Extracts the username and roles from a JWT token if the token is valid and unexpired.
     * Tokens issued before roles existed carry no {@value #ROLES_CLAIM} claim and count as
     * {@link Role#CLIENT}, since only clients could log in then.
     *
     * @param token the JWT token to parse
     * @return the token's claims, or empty if the token is invalid, expired, or names an unknown role
     */
    public java.util.Optional<TokenClaims> extractClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getSubject() == null) {
                return java.util.Optional.empty();
            }
            List<?> names = claims.get(ROLES_CLAIM, List.class);
            Set<Role> roles = names == null || names.isEmpty()
                    ? EnumSet.of(Role.CLIENT)
                    : names.stream().map(name -> Role.valueOf(String.valueOf(name)))
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
            return java.util.Optional.of(new TokenClaims(claims.getSubject(), roles));
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
