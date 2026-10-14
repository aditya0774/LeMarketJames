package com.lemarketjames.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Writes security events that cut off access (LMKT-57, AC3), so sign-in and every service's
 * request filter log them in one format. Names the account by username only: never a password,
 * token or email.
 */
public final class SecurityEventLog {

    /** Reason given when the token's account no longer exists. */
    public static final String REASON_NOT_FOUND = "NOT_FOUND";

    /** Reason given for a staff user whose login was deactivated. A client's reason is its AccountStatus name. */
    public static final String REASON_DEACTIVATED = "DEACTIVATED";

    private static final Logger log = LoggerFactory.getLogger(SecurityEventLog.class);

    private SecurityEventLog() {
    }

    /**
     * Records that an account was refused access.
     *
     * @param username the account's username
     * @param reason   why: the client's status name, {@link #REASON_DEACTIVATED} or {@link #REASON_NOT_FOUND}
     * @param at       when; {@link Instant#toString()} prints it in UTC (ISO-8601, ending in Z)
     */
    public static void accessRevoked(String username, String reason, Instant at) {
        log.warn("ACCESS_REVOKED username={} reason={} at={}", username, reason, at);
    }
}
