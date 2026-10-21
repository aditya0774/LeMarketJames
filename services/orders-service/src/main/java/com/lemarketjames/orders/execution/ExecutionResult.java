package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;

import java.math.BigDecimal;

/**
 * The outcome of {@link OrderExecutor#execute}: the status the order should move to next and,
 * depending on it, the quote it fills at or the rejection reason.
 *
 * @param nextStatus FILLED, REJECTED, or PENDING/DELAYED when it can't execute yet
 * @param quote      the quote behind the outcome: the one the order fills at (FILLED), or the one it
 *                   was refused on (REJECTED with STALE_QUOTE, or PRICE_UNAVAILABLE for an unusable
 *                   price) so the audit trail shows it; otherwise null
 * @param reason     why it was refused (REJECTED), or why it has to wait (DELAYED: MARKET_CLOSED);
 *                   otherwise null
 */
public record ExecutionResult(OrderStatus nextStatus, QuoteUsed quote, RejectionReason reason) {

    public static ExecutionResult filled(QuoteUsed quote) {
        return new ExecutionResult(OrderStatus.FILLED, quote, null);
    }

    public static ExecutionResult rejected(RejectionReason reason) {
        return new ExecutionResult(OrderStatus.REJECTED, null, reason);
    }

    /** A refusal on a quote the feed did return, kept so the audit trail can show it. */
    public static ExecutionResult rejected(RejectionReason reason, QuoteUsed refusedQuote) {
        return new ExecutionResult(OrderStatus.REJECTED, refusedQuote, reason);
    }

    public static ExecutionResult waiting(OrderStatus status, RejectionReason why) {
        return new ExecutionResult(status, null, why);
    }

    /** Price per share to fill at, or null when the order isn't filling. */
    public BigDecimal fillPrice() {
        return nextStatus != OrderStatus.FILLED || quote == null ? null : quote.price();
    }
}
