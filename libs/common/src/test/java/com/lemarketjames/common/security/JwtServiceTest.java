package com.lemarketjames.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-key-please-32bytes-minimum";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000);
    }

    // A token generated for a username should, when read back, yield that same username.
    @Test
    void generateTokenProducesTokenWithMatchingSubject() {
        String token = jwtService.generateToken("alice");

        assertEquals(Optional.of("alice"), jwtService.extractUsername(token));
    }

    // If a token's contents are altered after signing, it must no longer be trusted.
    @Test
    void extractUsernameReturnsEmptyForTamperedToken() {
        String token = jwtService.generateToken("alice");

        assertTrue(jwtService.extractUsername(token + "tampered").isEmpty());
    }

    // A token signed with a different secret key must be rejected.
    @Test
    void extractUsernameReturnsEmptyForTokenSignedWithDifferentKey() {
        JwtService otherService = new JwtService("a-completely-different-signing-key-32bytes+", 3600000);
        String token = otherService.generateToken("alice");

        assertTrue(jwtService.extractUsername(token).isEmpty());
    }

    // A token that has passed its expiration time must no longer be accepted.
    @Test
    void extractUsernameReturnsEmptyForExpiredToken() throws InterruptedException {
        JwtService shortLivedService = new JwtService(SECRET, 1);
        String token = shortLivedService.generateToken("alice");

        Thread.sleep(10);

        assertTrue(shortLivedService.extractUsername(token).isEmpty());
    }

    // The configured expiration in milliseconds should convert correctly to seconds.
    @Test
    void getExpirationSecondsConvertsMillisecondsToSeconds() {
        assertEquals(3600, jwtService.getExpirationSeconds());
    }

    // Roles written into a token come back unchanged, so every service can authorize from the token.
    @Test
    void rolesRoundTripThroughToken() {
        String token = jwtService.generateToken("olivia_ops", Set.of(Role.TRADING_OPS));

        TokenClaims claims = jwtService.extractClaims(token).orElseThrow();

        assertEquals("olivia_ops", claims.username());
        assertEquals(Set.of(Role.TRADING_OPS), claims.roles());
    }

    // The username-only overload is a client login.
    @Test
    void usernameOnlyTokenIsAClient() {
        String token = jwtService.generateToken("alice");

        assertEquals(Set.of(Role.CLIENT), jwtService.extractClaims(token).orElseThrow().roles());
    }

    // Tokens issued before roles existed have no roles claim; they were all client logins.
    @Test
    void tokenWithoutRolesClaimCountsAsClient() {
        String legacyToken = Jwts.builder()
                .subject("alice")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertEquals(Set.of(Role.CLIENT), jwtService.extractClaims(legacyToken).orElseThrow().roles());
    }
}
