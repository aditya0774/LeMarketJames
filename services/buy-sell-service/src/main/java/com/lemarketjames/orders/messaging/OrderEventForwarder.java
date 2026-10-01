package com.lemarketjames.orders.messaging;

import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.events.OrderStatusChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process order events (contract C6) to the {@link OrderEventPublisher}. It forwards
 * only after the order transaction commits, so a rolled-back transition is never announced; an event
 * published outside a transaction has nothing to wait for and is forwarded straight away.
 */
@Component
public class OrderEventForwarder {
    public static final String STATUS_CHANGED_TOPIC = "lemarket.orders.status-changed";
    public static final String FILLED_TOPIC = "lemarket.orders.filled";

    private final OrderEventPublisher publisher;

    public OrderEventForwarder(OrderEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onStatusChanged(OrderStatusChanged event) {
        publisher.publish(STATUS_CHANGED_TOPIC, key(event.orderId()), event);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onFilled(OrderFilled event) {
        publisher.publish(FILLED_TOPIC, key(event.orderId()), event);
    }

    /** Keyed by order so one order's events stay in sequence on a partitioned topic. */
    private static String key(Integer orderId) {
        return String.valueOf(orderId);
    }
}
