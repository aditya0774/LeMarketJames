package com.lemarketjames.orders.entity;

/**
 * Why an order was refused (contract C1). Used both as the {@code code} in a failed placement
 * response and as the value stored in {@code orders.rejection_reason} when an order is rejected.
 */
public enum RejectionReason {
    /** A BUY costs more than the account's cash. */
    INSUFFICIENT_CASH,
    /** A SELL is for more shares than the account holds. */
    INSUFFICIENT_HOLDINGS,
    /** The stock is suspended (instruments.tradable = false). */
    NOT_TRADABLE,
    /** No usable quote: the feed is down or returned no price. */
    PRICE_UNAVAILABLE,
    /** The quote is older than the staleness limit (lmj.market.staleness-limit). */
    STALE_QUOTE,
    /** The market is closed and the order can't wait for the open. */
    MARKET_CLOSED,
    /** The client or account may not trade: status isn't ACTIVE, or trading is disabled on the account. */
    ACCOUNT_RESTRICTED,
    /** The client lives somewhere trading isn't allowed (lmj.orders.restricted-locations). */
    LOCATION_RESTRICTED,
    /** Operations rejected the order by hand. */
    REJECTED_BY_OPERATIONS
}
