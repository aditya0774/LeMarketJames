package com.lemarketjames.orders.stream;

import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.events.OrderStatusChanged;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusStreamServiceTest {

    private final OrderStatusStreamService service = new OrderStatusStreamService();

    @Test
    void subscribeRegistersEmitter() {
        service.subscribe(7);
        assertEquals(1, subscriberCount(7));
    }

    @Test
    void publishForOtherAccountDoesNotTouchSubscribers() {
        service.subscribe(9);

        var event = new OrderStatusChanged(
            42,
            7,
            Order.OrderStatus.SUBMITTED,
            Order.OrderStatus.ACCEPTED,
            Instant.parse("2026-10-06T10:00:00Z")
        );
        service.publish(event);

        assertEquals(1, subscriberCount(9));
        assertEquals(0, subscriberCount(7));
    }

    @Test
    void publishRemovesEmitterWhenSendFails() {
        SseEmitter emitter = service.subscribe(7);
        emitter.complete();

        var event = new OrderStatusChanged(
            42,
            7,
            Order.OrderStatus.SUBMITTED,
            Order.OrderStatus.ACCEPTED,
            Instant.parse("2026-10-06T10:00:00Z")
        );
        service.publish(event);

        // Closed emitters should be evicted when publish hits a send failure.
        assertEquals(0, subscriberCount(7));
    }

    @SuppressWarnings("unchecked")
    private int subscriberCount(Integer accountId) {
        var subscribers = (ConcurrentHashMap<Integer, Set<SseEmitter>>) ReflectionTestUtils
            .getField(service, "subscribersByAccount");
        if (subscribers == null) {
            return 0;
        }
        return subscribers.getOrDefault(accountId, Set.of()).size();
    }
}
