package com.lemarketjames.orders.execution;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.events.OrderStatusChanged;
import java.time.Instant;
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
        var previous = order.getOrderStatus();
        if (next == Order.OrderStatus.REJECTED) order.reject(reason); else order.transitionTo(next);
        if (next == Order.OrderStatus.ACCEPTED) record(order, AuditEventType.ACCEPTED, Map.of());
        if (next == Order.OrderStatus.REJECTED) record(order, AuditEventType.REJECTED, Map.of("reason", reason.name()));
        if (next == Order.OrderStatus.FILLED) record(order, AuditEventType.FILLED,
            Map.of("quantity", order.getQuantity(), "price", order.getPricePerUnit()));
        Instant now = Instant.now();
        events.publishEvent(new OrderStatusChanged(order.getOrderId(), order.getAccountId(), previous, next, now));
        if (next == Order.OrderStatus.FILLED) events.publishEvent(new OrderFilled(order.getOrderId(),
            order.getAccountId(), order.getInstrumentId(), order.getOrderType(), order.getQuantity(), order.getPricePerUnit(), now));
    }
    private void record(Order order, AuditEventType type, Map<String, Object> details) {
        audit.record(type, order.getOrderId(), order.getAccountId(), details);
    }
}
