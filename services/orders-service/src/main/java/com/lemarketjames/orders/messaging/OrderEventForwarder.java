package com.lemarketjames.orders.messaging;

import com.lemarketjames.common.events.OrderEventTopicNames;
import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.events.OrderStatusChanged;
import com.lemarketjames.orders.events.OrderSubmitted;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process order events (contract C6) to the {@link OrderEventPublisher}. It forwards
 * only after the order transaction commits, so a rolled-back transition is never announced; an event
 * published outside a transaction has nothing to wait for and is forwarded straight away.
 */
@Component
public class OrderEventForwarder {
    // The names themselves are in libs/common, where the consuming services read them too.
    public static final String SUBMITTED_TOPIC = OrderEventTopicNames.SUBMITTED;
    public static final String STATUS_CHANGED_TOPIC = OrderEventTopicNames.STATUS_CHANGED;
    public static final String FILLED_TOPIC = OrderEventTopicNames.FILLED;

    private final OrderEventPublisher publisher;

    public OrderEventForwarder(OrderEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onSubmitted(OrderSubmitted event) {
        publisher.publish(SUBMITTED_TOPIC, key(event.orderId()), event);
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
