package com.lemarketjames.auth;

import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.common.security.TokenClaims;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * How long a session token lasts (BR-03). A token is valid for the inactivity timeout only
 * ({@code lmj.session.inactivity-timeout}, contract C5), so a session nobody uses ends on its own,
 * at every service, without any of them keeping session state. The web apps refresh it while the
 * user is active; each refresh keeps the sign-in time, and no token outlives the sign-in time plus
 * the session cap ({@code jwt.expiration-ms}), however often it is refreshed.
 *
 * <p>Kept apart from {@link AuthService} so the lifetime rule is one small, testable class; the
 * service decides who may sign in, this class only how long the result lasts.
 */
class SessionTokens {

    private final JwtService jwtService;
    private final Duration inactivityTimeout;
    private final Duration sessionCap;
    private final Clock clock;

    SessionTokens(JwtService jwtService, Duration inactivityTimeout, Duration sessionCap, Clock clock) {
        this.jwtService = jwtService;
        this.inactivityTimeout = inactivityTimeout;
        this.sessionCap = sessionCap;
        this.clock = clock;
    }

    /** A signed token and when it stops being accepted. */
    record SessionToken(String value, Instant expiresAt) {
    }

    /**
     * Issues the token for a sign-in happening now.
     *
     * @param issuedAt the issue time to stamp, when it must be later than now to land after the
     *                 account's last sign-out (see AuthService); null for now
     */
    SessionToken signIn(String username, Set<Role> roles, Instant issuedAt) {
        Instant now = clock.instant();
        Instant issued = issuedAt == null ? now : issuedAt;
        return issue(username, roles, issued, issued);
    }

    /**
     * A new token for the same session, valid for another inactivity timeout from now (or until
     * the cap). Empty when the token is no longer valid: expired, tampered with, or past the cap.
     * Whether the account was signed out or cut off since is checked before this is reached, by
     * the JWT filter, as for every request.
     */
    Optional<SessionToken> refresh(String token) {
        Instant now = clock.instant();
        return jwtService.extractClaims(token)
                .filter(claims -> now.isBefore(capFor(claims.authTime())))
                .map(claims -> issue(claims.username(), claims.roles(), refreshIssuedAt(claims, now), claims.authTime()));
    }

    /** How long a session may sit idle; the web apps time their warning from it. */
    Duration inactivityTimeout() {
        return inactivityTimeout;
    }

    /** When the given token stops being accepted, if it is valid now. */
    Optional<Instant> expiryOf(String token) {
        return jwtService.extractClaims(token).map(TokenClaims::expiresAt);
    }

    // Never earlier than the token being replaced. A sign-in in the same second as a sign-out is
    // stamped a second later (AuthService.loginIssuedAt), since tokens issued at or before the
    // sign-out are refused; restamping "now" would put the refreshed token back inside that second.
    private static Instant refreshIssuedAt(TokenClaims claims, Instant now) {
        return claims.issuedAt().isAfter(now) ? claims.issuedAt() : now;
    }

    private SessionToken issue(String username, Set<Role> roles, Instant issuedAt, Instant authTime) {
        Instant idleExpiry = issuedAt.plus(inactivityTimeout);
        Instant cap = capFor(authTime);
        Instant expiresAt = idleExpiry.isBefore(cap) ? idleExpiry : cap;
        return new SessionToken(jwtService.generateToken(username, roles, issuedAt, authTime, expiresAt), expiresAt);
    }

    private Instant capFor(Instant authTime) {
        return authTime.plus(sessionCap);
    }
}
