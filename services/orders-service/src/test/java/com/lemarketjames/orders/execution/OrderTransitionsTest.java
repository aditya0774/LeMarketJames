package com.lemarketjames.orders.execution;

import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.events.OrderStatusChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** What a status change announces about itself (contract C6): the reason travels with a rejection, and only with one. */
class OrderTransitionsTest {

    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final OrderTransitions transitions = new OrderTransitions(mock(AuditRecorder.class), events);

    private static Order orderIn(OrderStatus status) {
        Order order = new Order(7, 5, Order.OrderType.BUY, BigDecimal.ONE);
        order.setOrderId(42);
        order.setOrderStatus(status);
        return order;
    }

    private OrderStatusChanged published() {
        ArgumentCaptor<OrderStatusChanged> event = ArgumentCaptor.forClass(OrderStatusChanged.class);
        verify(events).publishEvent(event.capture());
        return event.getValue();
    }

    /** Every reason, so one added later is announced without anybody remembering to. */
    @ParameterizedTest
    @EnumSource(RejectionReason.class)
    void aRejectionAnnouncesItsReason(RejectionReason reason) {
        // PENDING is where a refused settlement leaves from.
        transitions.move(orderIn(OrderStatus.PENDING), OrderStatus.REJECTED, reason);

        OrderStatusChanged event = published();
        assertEquals(42, event.orderId());
        assertEquals(7, event.accountId());
        assertEquals(OrderStatus.PENDING, event.from());
        assertEquals(OrderStatus.REJECTED, event.to());
        assertEquals(reason, event.rejectionReason());
        assertNotNull(event.occurredAt());
    }

    @Test
    void aMoveThatIsNotARejectionAnnouncesNoReason() {
        transitions.move(orderIn(OrderStatus.SUBMITTED), OrderStatus.ACCEPTED, null);

        OrderStatusChanged event = published();
        assertEquals(OrderStatus.ACCEPTED, event.to());
        assertNull(event.rejectionReason());
    }
}
