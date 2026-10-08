package com.lemarketjames.surveillance;

/**
 * Why an order was brought to Trading Operations' attention. Stored by name in
 * {@code order_alerts.reason} and returned by the alerts endpoint (contract C6), so a name is
 * never changed once alerts exist; a new rule adds a new reason.
 */
public enum AlertReason {
    /** The order is for at least the large-order quantity (contract C5, {@code lmj.surveillance}). */
    LARGE_ORDER
}
