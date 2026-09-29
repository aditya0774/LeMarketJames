package com.lemarketjames.common.domain;

/**
 * The group a client belongs to for reporting and insights (contract C3). New registrations start
 * as {@link #RETAIL}.
 *
 * <p>Mirrors: the {@code clients.segment} CHECK constraint (database/schema/010).
 */
public enum ClientSegment {
    /** Occasional, small-balance investors; the default. */
    RETAIL,
    /** Trades frequently. */
    ACTIVE_TRADER,
    /** Large balances. */
    HIGH_NET_WORTH
}
