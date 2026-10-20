package com.lemarketjames.orders.stream;

import com.lemarketjames.orders.events.OrderStatusChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards committed order-status events to in-memory SSE subscribers.
 */
@Component
public class OrderStatusStreamListener {

    private final OrderStatusStreamService streams;

    public OrderStatusStreamListener(OrderStatusStreamService streams) {
        this.streams = streams;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        streams.publish(event);
    }
}
