package com.lemarketjames.surveillance;

import com.lemarketjames.common.config.PlatformSettings;
import org.junit.jupiter.api.Test;
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

/** Which new orders raise an alert. */
class SurveillanceServiceTest {

    static final Instant WHEN = Instant.parse("2026-10-08T14:30:00Z");

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
        when(stored.existsByOrderId(42)).thenReturn(true);

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

    @Test
    void staffReadTheNewestAlerts() {
        List<OrderAlert> newest = List.of(new OrderAlert(buy(large), AlertReason.LARGE_ORDER));
        when(stored.findAllByOrderBySubmittedAtDescAlertIdDesc(PageRequest.of(0, SurveillanceService.LATEST)))
            .thenReturn(newest);

        assertSame(newest, service.latest());
    }
}
