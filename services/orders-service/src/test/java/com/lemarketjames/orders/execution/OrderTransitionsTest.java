package com.lemarketjames.orders.execution;

import com.lemarketjames.common.audit.AuditEventType;
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
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * What a status change announces about itself (contract C6): the reason travels with a rejection,
 * and only with one. And what it audits (contract C2): every step after placement, waiting included.
 */
class OrderTransitionsTest {

    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final AuditRecorder audit = mock(AuditRecorder.class);
    final OrderTransitions transitions = new OrderTransitions(audit, events);

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

    // BR-15: the waiting steps are part of the trail, so a dispute can see how long an order waited.
    @Test
    void movingToPendingIsAudited() {
        transitions.move(orderIn(OrderStatus.ACCEPTED), OrderStatus.PENDING, null);

        verify(audit).record(AuditEventType.PENDING, 42, 7, Map.of());
    }

    @Test
    void aDelayIsAuditedWithWhyTheOrderWaits() {
        transitions.move(orderIn(OrderStatus.ACCEPTED), OrderStatus.DELAYED, RejectionReason.MARKET_CLOSED);

        verify(audit).record(AuditEventType.DELAYED, 42, 7, Map.of("reason", "MARKET_CLOSED"));
        // A delay is not a rejection: nothing is stored on the order, and nothing is announced as one.
        assertNull(published().rejectionReason());
    }

    @Test
    void aDelayWithoutAReasonIsAuditedWithEmptyDetails() {
        Order order = orderIn(OrderStatus.ACCEPTED);
        transitions.move(order, OrderStatus.DELAYED, null);

        verify(audit).record(AuditEventType.DELAYED, 42, 7, Map.of());
        assertNull(order.getRejectionReason());
    }

    @Test
    void aRejectionOnAQuoteRecordsThatQuote() {
        Instant quoteTime = Instant.parse("2026-09-30T14:59:00Z");
        transitions.move(orderIn(OrderStatus.ACCEPTED), OrderStatus.REJECTED, RejectionReason.STALE_QUOTE,
            new QuoteUsed(new BigDecimal("101.2346"), "SIMULATED_FEED", quoteTime));

        verify(audit).record(AuditEventType.REJECTED, 42, 7, Map.of(
            "reason", "STALE_QUOTE", "quotePrice", new BigDecimal("101.2346"), "quoteTime", "2026-09-30T14:59:00Z"));
    }

    @Test
    void aRejectionLeavesOutWhatTheQuoteDidNotHave() {
        transitions.move(orderIn(OrderStatus.ACCEPTED), OrderStatus.REJECTED, RejectionReason.STALE_QUOTE,
            new QuoteUsed(null, "SIMULATED_FEED", null));

        verify(audit).record(AuditEventType.REJECTED, 42, 7, Map.of("reason", "STALE_QUOTE"));
    }

    @Test
    void submittedIsLeftToPlacement() {
        transitions.move(orderIn(OrderStatus.SUBMITTED), OrderStatus.ACCEPTED, null);

        verify(audit).record(AuditEventType.ACCEPTED, 42, 7, Map.of());
        verify(audit, never()).record(eq(AuditEventType.SUBMITTED), eq(42), eq(7), anyMap());
    }
}
