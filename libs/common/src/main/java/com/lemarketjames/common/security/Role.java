package com.lemarketjames.common.security;

/**
 * Who a logged-in user is (contract C7, see contracts/C7-roles.md for which role may use which
 * views). Carried in the JWT's {@code roles} claim and exposed to Spring Security as the authority
 * {@code ROLE_<name>}, so endpoints guard with {@code hasRole("TRADING_OPS")} and so on.
 *
 * <p>Mirrors: the {@code staff_users.role} CHECK constraint (every value except CLIENT) and the
 * frontend's {@code Role} type in {@code apps/frontend/src/app/core/auth/auth.ts}.
 */
public enum Role {
    /** A retail trader with a trading account; the only role that places orders. */
    CLIENT,
    /** Staff who run order operations: may move orders through their lifecycle and reject them. */
    TRADING_OPS,
    /** Staff who read reports and insights; never see individual clients' personal details. */
    ANALYST,
    /** Staff who read the audit trail and reports. */
    COMPLIANCE;

    /** The Spring Security authority for this role, e.g. {@code ROLE_TRADING_OPS}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
