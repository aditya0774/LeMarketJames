package com.lemarketjames.orders.messaging;

import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.events.OrderStatusChanged;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class OrderEventForwarderTest {
    final OrderEventPublisher publisher = mock(OrderEventPublisher.class);
    final OrderEventForwarder forwarder = new OrderEventForwarder(publisher);
    final Instant now = Instant.parse("2026-09-30T15:00:00Z");

    @Test void forwardsStatusChangeKeyedByOrder() {
        var event = new OrderStatusChanged(7, 1, Order.OrderStatus.SUBMITTED, Order.OrderStatus.ACCEPTED, now);
        forwarder.onStatusChanged(event);
        verify(publisher).publish(OrderEventForwarder.STATUS_CHANGED_TOPIC, "7", event);
    }
    @Test void forwardsFillKeyedByOrder() {
        var event = new OrderFilled(7, 1, 3, Order.OrderType.BUY, BigDecimal.TEN, new BigDecimal("101.0000"), now,
            "SIMULATED_FEED", now.minusSeconds(1));
        forwarder.onFilled(event);
        verify(publisher).publish(OrderEventForwarder.FILLED_TOPIC, "7", event);
    }
    @Test void kafkaStubLogsTheRecordInsteadOfSendingIt(CapturedOutput output) {
        var event = new OrderStatusChanged(7, 1, Order.OrderStatus.ACCEPTED, Order.OrderStatus.FILLED, now);
        new OrderEventForwarder(new KafkaStubOrderEventPublisher()).onStatusChanged(event);
        assertTrue(output.getOut().contains("Kafka stub: would publish topic=" + OrderEventForwarder.STATUS_CHANGED_TOPIC + " key=7"));
    }
}
