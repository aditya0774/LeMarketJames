package com.lemarketjames.orders.submission;

/**
 * The checks an order goes through before it is saved, in the order they run. Each one that runs
 * is audited as a {@code RULE_CHECKED} event carrying its name (contract C2), so this enum is the
 * list the trade timeline displays.
 *
 * <p>Mirrors: the seeded checks in database/schema/015, which list the rules as they were before
 * QUANTITY was added (seeded orders were all placed by whole shares).
 */
public enum ValidationRule {
    /** The account belongs to the caller. Fails with {@code ACCOUNT_ACCESS_DENIED}. */
    ACCOUNT_ACCESS,
    /** Trading is enabled on the account and the client is ACTIVE. Fails with {@code ACCOUNT_RESTRICTED}. */
    ACCOUNT_STATUS,
    /**
     * The client doesn't live where this instrument, or every instrument, may not be traded
     * (instrument_location_restrictions, lmj.orders.restricted-locations). Fails with {@code LOCATION_RESTRICTED}.
     */
    LOCATION,
    /** The instrument exists and isn't suspended. Fails with {@code NOT_TRADABLE}. */
    TRADABLE,
    /**
     * A BUY, or an order placed by amount (BR-04): the feed has a usable price, the ask for a BUY and
     * the bid for a SELL. Fails with {@code PRICE_UNAVAILABLE}.
     */
    PRICE_AVAILABLE,
    /** Whenever PRICE_AVAILABLE ran: that quote is within the staleness limit. Fails with {@code STALE_QUOTE}. */
    QUOTE_FRESH,
    /**
     * The order is for a whole number of shares, at least one (BR-04). An order by amount is first
     * turned into as many whole shares as the amount covers at that price, which is why this rule
     * runs after the price checks. Fails with {@code INVALID_QUANTITY}.
     */
    QUANTITY,
    /** SELL only: the account holds enough shares not already in open SELLs. Fails with {@code INSUFFICIENT_HOLDINGS}. */
    HOLDINGS,
    /** BUY only: the cash not reserved by open BUYs covers the order at that price. Fails with {@code INSUFFICIENT_CASH}. */
    CASH
}
