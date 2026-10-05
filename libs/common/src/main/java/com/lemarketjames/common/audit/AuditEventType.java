package com.lemarketjames.common.audit;

/**
 * The steps of an order's life that are written to the audit trail (contract C2). Each constant's
 * Javadoc lists the keys its {@code details} JSON carries; writers and readers rely on them.
 *
 * <p>Mirrors: the {@code audit_log.action} CHECK constraint (database/schema/010 and 014), and the
 * seed events in database/schema/011.
 */
public enum AuditEventType {
    /**
     * An order was submitted, whether or not it was then saved. Details: {@code side},
     * {@code quantity}, {@code price} (null for a SELL, or a BUY refused before it was priced),
     * {@code instrumentId}, and {@code requestedAccountId} only when the caller was refused access
     * to that account.
     */
    SUBMITTED,
    /**
     * One placement check ran. Details: {@code rule} (a buy-sell ValidationRule), {@code result}
     * (PASS, FAIL or ERROR) and, on FAIL, {@code reason} (the code the caller was given).
     */
    RULE_CHECKED,
    /**
     * Every placement check passed and the order was saved. Details: {@code checks} (array of the
     * rules that ran, e.g. ["ACCOUNT_ACCESS", "ACCOUNT_STATUS", "LOCATION", "TRADABLE", "HOLDINGS"]).
     */
    VALIDATED,
    /** The order was accepted for execution. Details: none ({@code {}}). */
    ACCEPTED,
    /** The order executed. Details: {@code quantity}, {@code price}. */
    FILLED,
    /** The order was refused. Details: {@code reason} (an order RejectionReason code). */
    REJECTED,
    /** Cash and holdings were updated for a fill. Details: {@code cashDelta}, {@code quantityDelta}. */
    SETTLED
}
