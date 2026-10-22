package com.lemarketjames.common.domain;

/**
 * A client's standing (contract C3/C7). Decides whether the client may log in and trade.
 *
 * <p>Who changes it (BR-03): Trading Ops, through auth-service's account status endpoint, and a client
 * closing their own account. Each change gets a row in {@code account_status_changes}.
 *
 * <p>Mirrors: the {@code clients.account_status} CHECK constraint (database/schema/010), the
 * {@code account_status_changes} CHECK constraints (027) and the staff app's {@code AccountStatus}
 * type (apps/frontend/projects/staff/src/app/core/clients/client-status.service.ts).
 */
public enum AccountStatus {
    /** Normal: may log in and, if the account has trading enabled, trade. */
    ACTIVE,
    /** Blocked by operations (e.g. under investigation): may not log in. */
    SUSPENDED,
    /** Closed at the client's request: may not log in. */
    CLOSED,
    /** Identity or KYC documents lapsed: may not log in (LMKT-57) and may not trade until renewed. */
    EXPIRED;

    /**
     * Whether a client in this status may log in at all. Only ACTIVE may: every other status loses
     * access at sign-in and, for a session opened before the change, on its next request (LMKT-57).
     */
    public boolean canLogIn() {
        return this == ACTIVE;
    }

    /** Whether a client in this status may place orders (the account must also have trading enabled). */
    public boolean canTrade() {
        return this == ACTIVE;
    }
}
