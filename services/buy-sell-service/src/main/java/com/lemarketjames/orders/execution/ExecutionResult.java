package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;

import java.math.BigDecimal;

/**
 * The outcome of {@link OrderExecutor#execute}: the status the order should move to next and,
 * depending on it, the quote it fills at or the rejection reason.
 *
 * @param nextStatus FILLED, REJECTED, or PENDING/DELAYED when it can't execute yet
 * @param quote      the quote the order fills at; set only when nextStatus is FILLED
 * @param reason     why it was refused; set only when nextStatus is REJECTED
 */
public record ExecutionResult(OrderStatus nextStatus, QuoteUsed quote, RejectionReason reason) {

    public static ExecutionResult filled(QuoteUsed quote) {
        return new ExecutionResult(OrderStatus.FILLED, quote, null);
    }

    public static ExecutionResult rejected(RejectionReason reason) {
        return new ExecutionResult(OrderStatus.REJECTED, null, reason);
    }

    public static ExecutionResult waiting(OrderStatus status) {
        return new ExecutionResult(status, null, null);
    }

    /** Price per share to fill at, or null when the order isn't filling. */
    public BigDecimal fillPrice() {
        return quote == null ? null : quote.price();
    }
}
