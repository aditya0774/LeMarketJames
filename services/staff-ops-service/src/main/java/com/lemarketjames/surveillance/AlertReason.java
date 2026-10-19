package com.lemarketjames.surveillance;

/**
 * Why an order was brought to Trading Operations' attention. Stored by name in
 * {@code order_alerts.reason} and returned by the alerts endpoint (contract C6), so a name is
 * never changed once alerts exist; a new rule adds a new reason.
 */
public enum AlertReason {
    /** The order is for at least the large-order quantity (contract C5, {@code lmj.surveillance}). */
    LARGE_ORDER,
    /**
     * The order was priced and sent to settlement, and trading-service refused it: by then the
     * account no longer had the cash (a BUY) or the shares (a SELL). The alert's rejection reason
     * says which (contract C6).
     */
    SETTLEMENT_REFUSED
}
