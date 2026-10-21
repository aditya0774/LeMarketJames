package com.lemarketjames.orders.execution;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.events.OrderStatusChanged;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Used inside the caller's order transaction, including recovery after a lost settlement response. */
@Component
public class OrderTransitions {
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    public OrderTransitions(AuditRecorder audit, ApplicationEventPublisher events) {
        this.audit = audit; this.events = events;
    }
    public void move(Order order, Order.OrderStatus next, RejectionReason reason) {
        move(order, next, reason, null);
    }
    /**
     * Moves the order and audits the step (contract C2), every status but SUBMITTED: placement
     * audits that. {@code reason} is the rejection reason for REJECTED, and why it waits for DELAYED;
     * {@code quote} is the quote a rejection was made on, when there was one.
     */
    public void move(Order order, Order.OrderStatus next, RejectionReason reason, QuoteUsed quote) {
        var previous = order.getOrderStatus();
        if (next == Order.OrderStatus.REJECTED) order.reject(reason); else order.transitionTo(next);
        if (next == Order.OrderStatus.ACCEPTED) record(order, AuditEventType.ACCEPTED, Map.of());
        // PENDING and DELAYED are audited too, so a dispute can see how long an order waited and why.
        if (next == Order.OrderStatus.PENDING) record(order, AuditEventType.PENDING, Map.of());
        if (next == Order.OrderStatus.DELAYED) record(order, AuditEventType.DELAYED,
            reason == null ? Map.of() : Map.of("reason", reason.name()));
        if (next == Order.OrderStatus.REJECTED) record(order, AuditEventType.REJECTED, rejectionDetails(reason, quote));
        if (next == Order.OrderStatus.FILLED) record(order, AuditEventType.FILLED,
            Map.of("quantity", order.getQuantity(), "price", order.getPricePerUnit()));
        Instant now = Instant.now();
        // The reason travels only with a rejection: staff-ops-service alerts on some of them (contract C6).
        events.publishEvent(new OrderStatusChanged(order.getOrderId(), order.getAccountId(), previous, next, now,
            next == Order.OrderStatus.REJECTED ? reason : null));
        if (next == Order.OrderStatus.FILLED) events.publishEvent(new OrderFilled(order.getOrderId(),
            order.getAccountId(), order.getInstrumentId(), order.getOrderType(), order.getQuantity(), order.getPricePerUnit(), now,
            order.getQuoteSource(), order.getQuoteTime()));
    }
    private static Map<String, Object> rejectionDetails(RejectionReason reason, QuoteUsed quote) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name());
        if (quote != null && quote.price() != null) details.put("quotePrice", quote.price());
        // A string, so the stored JSON reads the same as every other instant in the trail.
        if (quote != null && quote.quoteTime() != null) details.put("quoteTime", quote.quoteTime().toString());
        return details;
    }
    private void record(Order order, AuditEventType type, Map<String, Object> details) {
        audit.record(type, order.getOrderId(), order.getAccountId(), details);
    }
}
