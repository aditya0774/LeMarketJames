package com.lemarketjames.surveillance;

import com.lemarketjames.common.config.PlatformSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which new orders, and which status changes, raise an alert. */
class SurveillanceServiceTest {

    static final Instant WHEN = Instant.parse("2026-10-08T14:30:00Z");
    /** A settlement is refused after the order was placed. */
    static final Instant REFUSED_AT = Instant.parse("2026-10-08T14:30:02Z");

    final OrderAlertRepository stored = mock(OrderAlertRepository.class);
    final PlatformSettings settings = new PlatformSettings();
    final SurveillanceService service = new SurveillanceService(stored, settings);

    /** The quantity that counts as large in this test, whatever the default is. */
    final BigDecimal large = settings.getSurveillance().getLargeOrderQuantity();

    private OrderSubmittedMessage buy(BigDecimal quantity) {
        return new OrderSubmittedMessage(42, 7, 5, "BUY", quantity, new BigDecimal("244.2366"), WHEN);
    }

    @Test
    void anOrderOfTheLargeQuantityRaisesAnAlertWithWhatWasOrdered() {
        assertTrue(service.review(buy(large)));

        ArgumentCaptor<OrderAlert> saved = ArgumentCaptor.forClass(OrderAlert.class);
        verify(stored).save(saved.capture());
        assertEquals(42, saved.getValue().getOrderId());
        assertEquals(7, saved.getValue().getAccountId());
        assertEquals(5, saved.getValue().getInstrumentId());
        assertEquals("BUY", saved.getValue().getSide());
        assertEquals(large, saved.getValue().getQuantity());
        assertEquals(new BigDecimal("244.2366"), saved.getValue().getPrice());
        assertEquals(AlertReason.LARGE_ORDER, saved.getValue().getReason());
        assertEquals(WHEN, saved.getValue().getSubmittedAt());
        // A large order is alerted to as it is placed, so that is when the alert happened.
        assertEquals(WHEN, saved.getValue().getOccurredAt());
        assertNull(saved.getValue().getRejectionReason());
    }

    @Test
    void aLargerOrderRaisesAnAlertToo() {
        assertTrue(service.review(buy(large.add(BigDecimal.ONE))));
    }

    @Test
    void anOrderJustBelowTheLargeQuantityRaisesNone() {
        assertFalse(service.review(buy(large.subtract(new BigDecimal("0.0001")))));

        verify(stored, never()).save(any());
    }

    /** A SELL has no price when it is placed; its size alone decides. */
    @Test
    void aLargeSellRaisesAnAlertWithoutAPrice() {
        assertTrue(service.review(new OrderSubmittedMessage(43, 7, 5, "SELL", large, null, WHEN)));

        ArgumentCaptor<OrderAlert> saved = ArgumentCaptor.forClass(OrderAlert.class);
        verify(stored).save(saved.capture());
        assertEquals("SELL", saved.getValue().getSide());
        assertNull(saved.getValue().getPrice());
    }

    @Test
    void theLargeQuantityIsTheEnvironmentsSetting() {
        settings.getSurveillance().setLargeOrderQuantity(new BigDecimal("10"));

        assertTrue(service.review(buy(new BigDecimal("10"))));
        assertEquals(new BigDecimal("10"), service.largeOrderQuantity());
    }

    /** Kafka redelivers after a restart or a failed commit; staff must not see the order twice. */
    @Test
    void anOrderThatAlreadyHasItsAlertGetsNoSecondOne() {
        when(stored.existsByOrderIdAndReason(42, AlertReason.LARGE_ORDER)).thenReturn(true);

        assertFalse(service.review(buy(large)));

        verify(stored, never()).save(any());
    }

    @Test
    void anEventWithoutAFieldAnAlertNeedsIsSkipped() {
        BigDecimal price = new BigDecimal("244.2366");
        for (OrderSubmittedMessage incomplete : List.of(
                new OrderSubmittedMessage(null, 7, 5, "BUY", large, price, WHEN),
                new OrderSubmittedMessage(42, null, 5, "BUY", large, price, WHEN),
                new OrderSubmittedMessage(42, 7, null, "BUY", large, price, WHEN),
                new OrderSubmittedMessage(42, 7, 5, null, large, price, WHEN),
                new OrderSubmittedMessage(42, 7, 5, "BUY", null, price, WHEN),
                new OrderSubmittedMessage(42, 7, 5, "BUY", large, price, null))) {
            assertFalse(service.review(incomplete), incomplete.toString());
        }

        verify(stored, never()).save(any());
    }

    private static OrderStatusChangedMessage movedTo(String status, String rejectionReason) {
        return new OrderStatusChangedMessage(42, 7, status, rejectionReason, REFUSED_AT);
    }

    /** Both ways holdings-service refuses a settlement: no cash for a BUY, no shares for a SELL. */
    @ParameterizedTest
    @ValueSource(strings = {"INSUFFICIENT_CASH", "INSUFFICIENT_HOLDINGS"})
    void aRefusedSettlementRaisesAnAlertThatSaysWhy(String refusal) {
        assertTrue(service.reviewStatusChange(movedTo("REJECTED", refusal)));

        ArgumentCaptor<OrderAlert> saved = ArgumentCaptor.forClass(OrderAlert.class);
        verify(stored).save(saved.capture());
        assertEquals(42, saved.getValue().getOrderId());
        assertEquals(7, saved.getValue().getAccountId());
        assertEquals(AlertReason.SETTLEMENT_REFUSED, saved.getValue().getReason());
        assertEquals(refusal, saved.getValue().getRejectionReason());
        assertEquals(REFUSED_AT, saved.getValue().getOccurredAt());
        // The status change does not say what was ordered.
        assertNull(saved.getValue().getInstrumentId());
        assertNull(saved.getValue().getSide());
        assertNull(saved.getValue().getQuantity());
        assertNull(saved.getValue().getPrice());
        assertNull(saved.getValue().getSubmittedAt());
    }

    /** Every other reason an order is rejected for (contract C1) is not a refused settlement. */
    @ParameterizedTest
    @ValueSource(strings = {"NOT_TRADABLE", "PRICE_UNAVAILABLE", "STALE_QUOTE", "MARKET_CLOSED", "ACCOUNT_RESTRICTED",
        "LOCATION_RESTRICTED", "REJECTED_BY_OPERATIONS", "A_REASON_ADDED_LATER"})
    void aRejectionForAnotherReasonRaisesNone(String reason) {
        assertFalse(service.reviewStatusChange(movedTo("REJECTED", reason)));

        verify(stored, never()).save(any());
    }

    /** A record published before the event carried a reason has none (contract C6). */
    @Test
    void aRejectionWithoutAReasonRaisesNone() {
        assertFalse(service.reviewStatusChange(movedTo("REJECTED", null)));

        verify(stored, never()).save(any());
    }

    /** Only a rejection is a refusal, whatever else a record might carry. */
    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "PENDING", "DELAYED", "FILLED"})
    void aChangeToAnyOtherStatusRaisesNone(String status) {
        assertFalse(service.reviewStatusChange(movedTo(status, null)));
        assertFalse(service.reviewStatusChange(movedTo(status, "INSUFFICIENT_CASH")));

        verify(stored, never()).save(any());
    }

    /** Kafka redelivers after a restart or a failed commit; staff must not see the refusal twice. */
    @Test
    void aRefusalThatAlreadyHasItsAlertGetsNoSecondOne() {
        when(stored.existsByOrderIdAndReason(42, AlertReason.SETTLEMENT_REFUSED)).thenReturn(true);

        assertFalse(service.reviewStatusChange(movedTo("REJECTED", "INSUFFICIENT_CASH")));

        verify(stored, never()).save(any());
    }

    /** One alert per order and reason: the alert for its size does not stand in for the one for its refusal. */
    @Test
    void aLargeOrderThatIsThenRefusedGetsBothAlerts() {
        when(stored.existsByOrderIdAndReason(42, AlertReason.LARGE_ORDER)).thenReturn(true);

        assertTrue(service.reviewStatusChange(movedTo("REJECTED", "INSUFFICIENT_CASH")));
    }

    @Test
    void aStatusChangeWithoutAFieldAnAlertNeedsIsSkipped() {
        for (OrderStatusChangedMessage incomplete : List.of(
                new OrderStatusChangedMessage(null, 7, "REJECTED", "INSUFFICIENT_CASH", REFUSED_AT),
                new OrderStatusChangedMessage(42, null, "REJECTED", "INSUFFICIENT_CASH", REFUSED_AT),
                new OrderStatusChangedMessage(42, 7, null, "INSUFFICIENT_CASH", REFUSED_AT),
                new OrderStatusChangedMessage(42, 7, "REJECTED", "INSUFFICIENT_CASH", null))) {
            assertFalse(service.reviewStatusChange(incomplete), incomplete.toString());
        }

        verify(stored, never()).save(any());
    }

    @Test
    void staffReadTheNewestAlerts() {
        List<OrderAlert> newest = List.of(new OrderAlert(buy(large), AlertReason.LARGE_ORDER));
        when(stored.findAllByOrderByOccurredAtDescAlertIdDesc(PageRequest.of(0, SurveillanceService.LATEST)))
            .thenReturn(newest);

        assertSame(newest, service.latest());
    }
}
