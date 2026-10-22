package com.lemarketjames.common.audit;

/**
 * The steps of an order's life that are written to the audit trail (contract C2). Each constant's
 * Javadoc lists the keys its {@code details} JSON carries; writers and readers rely on them.
 *
 * <p>Mirrors: the {@code audit_log.action} CHECK constraint, in every migration that defines it
 * (database/schema/010, 014, 024 and 026), and the
 * seed events in database/schema/011 and 015.
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
    /**
     * The order is executing: it has a price and its settlement is under way, or its last attempt
     * failed and it will be retried. Details: none ({@code {}}).
     */
    PENDING,
    /**
     * The order is waiting for its market to open. Details: {@code reason} ({@code MARKET_CLOSED})
     * when execution delayed it; none ({@code {}}) when it was moved by the status endpoint.
     */
    DELAYED,
    /** The order executed. Details: {@code quantity}, {@code price}. */
    FILLED,
    /**
     * The order was refused. Details: {@code reason} (an order RejectionReason code) and, when
     * execution refused it on a quote it did receive ({@code STALE_QUOTE}, or {@code PRICE_UNAVAILABLE}
     * for an unusable price), that quote's {@code quotePrice} (the ask for a BUY, the bid for a SELL;
     * left out when it isn't a number) and {@code quoteTime} (ISO-8601 UTC; left out when the quote
     * had none).
     */
    REJECTED,
    /** Cash and holdings were updated for a fill. Details: {@code cashDelta}, {@code quantityDelta}. */
    SETTLED,
    /** Cash was deposited. Details: {@code amount}, {@code balanceAfter}, {@code requestId}. */
    CASH_DEPOSITED,
    /** Cash was withdrawn. Details: {@code amount}, {@code balanceAfter}, {@code requestId}. */
    CASH_WITHDRAWN
}
