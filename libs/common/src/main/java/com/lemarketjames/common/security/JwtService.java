package com.lemarketjames.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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

    /**
     * The claim holding when the holder signed in (the OpenID Connect name). A refreshed token
     * keeps it, so a session can be capped however often it is refreshed (BR-03).
     */
    public static final String AUTH_TIME_CLAIM = "auth_time";

    private final SecretKey signingKey;
    private final long expirationMs;
    private final Clock clock;

    /**
     * Constructs a JwtService with the provided secret key and expiration time.
     *
     * @param secret the secret key string for signing tokens
     * @param expirationMs the longest a session may last, in milliseconds: auth-service caps every
     *                     session at this however often it is refreshed (BR-03); tokens issued
     *                     without an explicit expiry also last this long
     */
    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this(secret, expirationMs, Clock.systemUTC());
    }

    /**
     * Takes the clock tokens are issued and checked against, so tests can move time instead of
     * waiting for a token to expire.
     */
    public JwtService(String secret, long expirationMs, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
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
        Instant now = clock.instant();
        return generateToken(username, roles, now, now, now.plusMillis(expirationMs));
    }

    /** Generates a token whose issue time is explicitly after a session invalidation cutoff. */
    public String generateToken(String username, Set<Role> roles, Instant issuedAt) {
        Instant expiry = (issuedAt.isAfter(clock.instant()) ? issuedAt : clock.instant()).plusMillis(expirationMs);
        return generateToken(username, roles, issuedAt, issuedAt, expiry);
    }

    /**
     * Generates a session token with every time stated: auth-service uses it to make a token last
     * only as long as the inactivity timeout, and to carry the sign-in time over on refresh (BR-03).
     *
     * @param username  the token subject
     * @param roles     the holder's roles
     * @param issuedAt  the issue time; must be after the account's last sign-out to be accepted
     * @param authTime  when the holder signed in, stored in the {@value #AUTH_TIME_CLAIM} claim
     * @param expiresAt when the token stops being accepted
     * @return a signed JWT token string
     */
    public String generateToken(String username, Set<Role> roles, Instant issuedAt, Instant authTime,
                                Instant expiresAt) {
        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, roles.stream().map(Role::name).sorted().toList())
                .claim(AUTH_TIME_CLAIM, authTime.getEpochSecond())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
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
     * {@link Role#CLIENT}, since only clients could log in then. Tokens without
     * {@value #AUTH_TIME_CLAIM} were issued at sign-in, so their sign-in time is their issue time.
     *
     * @param token the JWT token to parse
     * @return the token's claims, or empty if the token is invalid, expired, or names an unknown role
     */
    public java.util.Optional<TokenClaims> extractClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
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
            Date issuedAt = claims.getIssuedAt();
            Instant issuedInstant = issuedAt == null ? Instant.MIN : issuedAt.toInstant();
            Number authTime = claims.get(AUTH_TIME_CLAIM, Number.class);
            Instant authInstant = authTime == null ? issuedInstant : Instant.ofEpochSecond(authTime.longValue());
            Date expiration = claims.getExpiration();
            Instant expiresInstant = expiration == null ? Instant.MAX : expiration.toInstant();
            return java.util.Optional.of(
                    new TokenClaims(claims.getSubject(), roles, issuedInstant, authInstant, expiresInstant));
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
