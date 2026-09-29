package com.lemarketjames.common.audit;

/**
 * The steps of an order's life that are written to the audit trail (contract C2). Each constant's
 * Javadoc lists the keys its {@code details} JSON carries; writers and readers rely on them.
 *
 * <p>Mirrors: the {@code audit_log.action} CHECK constraint (database/schema/010), and the seed
 * events in database/schema/011.
 */
public enum AuditEventType {
    /** The order was saved. Details: {@code side}, {@code quantity}, {@code price} (null for SELL). */
    SUBMITTED,
    /** Placement checks passed. Details: {@code checks} (array, e.g. ["ACCOUNT", "TRADABLE", "CASH"]). */
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
