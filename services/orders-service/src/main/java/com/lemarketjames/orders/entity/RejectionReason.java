package com.lemarketjames.orders.entity;

/**
 * Why an order was refused (contract C1). Used both as the {@code code} in a failed placement
 * response and as the value stored in {@code orders.rejection_reason} when an order is rejected.
 */
public enum RejectionReason {
    /** A BUY costs more than the account's cash less what its open BUYs reserve. */
    INSUFFICIENT_CASH,
    /** A SELL is for more shares than the account holds outside its open SELLs. */
    INSUFFICIENT_HOLDINGS,
    /** The stock is suspended (instruments.tradable = false). */
    NOT_TRADABLE,
    /**
     * BR-04: the order isn't for a whole number of shares (at least one), or its dollar amount
     * doesn't cover the price of one share.
     */
    INVALID_QUANTITY,
    /** No usable quote: the feed is down or returned no price. */
    PRICE_UNAVAILABLE,
    /** The quote is older than the staleness limit (lmj.market.staleness-limit). */
    STALE_QUOTE,
    /**
     * The market is closed and the order can't wait for the open. Also the {@code reason} on a
     * DELAYED audit event, when execution makes an order wait for the open (contract C2).
     */
    MARKET_CLOSED,
    /** The client or account may not trade: status isn't ACTIVE, or trading is disabled on the account. */
    ACCOUNT_RESTRICTED,
    /**
     * The client lives where this stock may not be traded: a location in lmj.orders.restricted-locations
     * (every stock) or in the stock's instrument_location_restrictions rows.
     */
    LOCATION_RESTRICTED,
    /** Operations rejected the order by hand. */
    REJECTED_BY_OPERATIONS
}
