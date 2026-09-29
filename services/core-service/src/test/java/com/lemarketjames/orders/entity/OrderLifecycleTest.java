package com.lemarketjames.orders.entity;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Contract C1: the order lifecycle, one row per move. */
class OrderLifecycleTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
        "SUBMITTED, ACCEPTED, true",
        "SUBMITTED, REJECTED, true",
        "SUBMITTED, FILLED,   false",
        "SUBMITTED, PENDING,  false",
        "ACCEPTED,  PENDING,  true",
        "ACCEPTED,  DELAYED,  true",
        "ACCEPTED,  FILLED,   true",
        "ACCEPTED,  REJECTED, true",
        "DELAYED,   PENDING,  true",
        "DELAYED,   REJECTED, true",
        "DELAYED,   FILLED,   false",
        "PENDING,   FILLED,   true",
        "PENDING,   REJECTED, true",
        "PENDING,   ACCEPTED, false",
        "FILLED,    REJECTED, false",
        "REJECTED,  ACCEPTED, false",
    })
    void transitionTable(OrderStatus from, OrderStatus to, boolean allowed) {
        Order order = new Order(1, 1, Order.OrderType.BUY, BigDecimal.ONE);
        order.setOrderStatus(from);

        if (allowed) {
            order.transitionTo(to);
            assertEquals(to, order.getOrderStatus());
        } else {
            assertThrows(InvalidStatusTransitionException.class, () -> order.transitionTo(to));
            assertEquals(from, order.getOrderStatus());
        }
    }

    @Test
    void newOrdersStartSubmitted() {
        assertEquals(OrderStatus.SUBMITTED, new Order(1, 1, Order.OrderType.BUY, BigDecimal.ONE).getOrderStatus());
    }

    @Test
    void acceptingAndFillingStampTheirTimes() {
        Order order = new Order(1, 1, Order.OrderType.BUY, BigDecimal.ONE);

        order.transitionTo(OrderStatus.ACCEPTED);
        assertNotNull(order.getAcceptedAt());
        order.transitionTo(OrderStatus.FILLED);
        assertNotNull(order.getFilledAt());
    }

    @Test
    void rejectingStoresTheReasonCode() {
        Order order = new Order(1, 1, Order.OrderType.SELL, BigDecimal.ONE);

        order.reject(RejectionReason.INSUFFICIENT_HOLDINGS);

        assertEquals(OrderStatus.REJECTED, order.getOrderStatus());
        assertEquals("INSUFFICIENT_HOLDINGS", order.getRejectionReason());
    }

    @Test
    void finalStatusesAreNotOpen() {
        assertFalse(OrderStatus.FILLED.isOpen());
        assertFalse(OrderStatus.REJECTED.isOpen());
        assertTrue(OrderStatus.DELAYED.isOpen());
    }
}
