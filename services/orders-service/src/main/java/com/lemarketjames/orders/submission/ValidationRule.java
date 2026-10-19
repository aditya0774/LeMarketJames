package com.lemarketjames.orders.submission;

/**
 * The checks an order goes through before it is saved, in the order they run. Each one that runs
 * is audited as a {@code RULE_CHECKED} event carrying its name (contract C2), so this enum is the
 * list the trade timeline displays.
 *
 * <p>Mirrors: the seeded checks in database/schema/015.
 */
public enum ValidationRule {
    /** The account belongs to the caller. Fails with {@code ACCOUNT_ACCESS_DENIED}. */
    ACCOUNT_ACCESS,
    /** Trading is enabled on the account and the client is ACTIVE. Fails with {@code ACCOUNT_RESTRICTED}. */
    ACCOUNT_STATUS,
    /** The client doesn't live somewhere trading is restricted. Fails with {@code LOCATION_RESTRICTED}. */
    LOCATION,
    /** The instrument exists and isn't suspended. Fails with {@code NOT_TRADABLE}. */
    TRADABLE,
    /** SELL only: the account holds enough shares. Fails with {@code INSUFFICIENT_HOLDINGS}. */
    HOLDINGS,
    /** BUY only: the feed has a usable ask price. Fails with {@code PRICE_UNAVAILABLE}. */
    PRICE_AVAILABLE,
    /** BUY only: that quote is within the staleness limit. Fails with {@code STALE_QUOTE}. */
    QUOTE_FRESH,
    /** BUY only: the account has enough cash at that price. Fails with {@code INSUFFICIENT_CASH}. */
    CASH
}
