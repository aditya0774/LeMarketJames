package com.lemarketjames.common.domain;

/**
 * A client's standing (contract C3/C7). Decides whether the client may log in and trade.
 *
 * <p>Mirrors: the {@code clients.account_status} CHECK constraint (database/schema/010).
 */
public enum AccountStatus {
    /** Normal: may log in and, if the account has trading enabled, trade. */
    ACTIVE,
    /** Blocked by operations (e.g. under investigation): may not log in. */
    SUSPENDED,
    /** Closed at the client's request: may not log in. */
    CLOSED,
    /** Identity or KYC documents lapsed: may log in and view, but may not trade until renewed. */
    EXPIRED;

    /** Whether a client in this status may log in at all. */
    public boolean canLogIn() {
        return this == ACTIVE || this == EXPIRED;
    }

    /** Whether a client in this status may place orders (the account must also have trading enabled). */
    public boolean canTrade() {
        return this == ACTIVE;
    }
}
