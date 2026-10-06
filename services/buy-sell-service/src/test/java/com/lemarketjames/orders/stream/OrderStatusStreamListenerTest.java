package com.lemarketjames.orders.stream;

import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.events.OrderStatusChanged;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderStatusStreamListenerTest {

    @Test
    void forwardsCommittedOrderStatusChangeToStreamService() {
        OrderStatusStreamService streams = mock(OrderStatusStreamService.class);
        OrderStatusStreamListener listener = new OrderStatusStreamListener(streams);

        var event = new OrderStatusChanged(
            42,
            7,
            Order.OrderStatus.SUBMITTED,
            Order.OrderStatus.ACCEPTED,
            Instant.parse("2026-10-06T10:00:00Z")
        );

        listener.onOrderStatusChanged(event);

        verify(streams).publish(event);
    }
}
