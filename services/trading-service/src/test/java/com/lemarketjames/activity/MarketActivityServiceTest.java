package com.lemarketjames.activity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which fills are recorded, and which window activity is summed over. */
class MarketActivityServiceTest {

    static final Instant NOW = Instant.parse("2026-10-08T14:30:00Z");

    final RecordedFillRepository stored = mock(RecordedFillRepository.class);
    final MarketActivityService service = new MarketActivityService(stored, Clock.fixed(NOW, ZoneOffset.UTC));

    private static OrderFilledMessage fill() {
        return new OrderFilledMessage(42, 5, "BUY", new BigDecimal("10"), new BigDecimal("244.2366"), NOW);
    }

    @Test
    void aFillIsStoredUnderItsOrder() {
        assertTrue(service.record(fill()));

        ArgumentCaptor<RecordedFill> saved = ArgumentCaptor.forClass(RecordedFill.class);
        verify(stored).save(saved.capture());
        assertEquals(42, saved.getValue().getOrderId());
        assertEquals(5, saved.getValue().getInstrumentId());
        assertEquals("BUY", saved.getValue().getSide());
        assertEquals(new BigDecimal("10"), saved.getValue().getQuantity());
        assertEquals(new BigDecimal("244.2366"), saved.getValue().getPrice());
        assertEquals(NOW, saved.getValue().getFilledAt());
    }

    /** Kafka redelivers after a restart or a failed commit; a trade must not be counted twice. */
    @Test
    void aFillThatIsAlreadyStoredIsNotStoredAgain() {
        when(stored.existsById(42)).thenReturn(true);

        assertFalse(service.record(fill()));

        verify(stored, never()).save(any());
    }

    @Test
    void anEventWithoutAFieldTheRecordNeedsIsSkipped() {
        BigDecimal ten = new BigDecimal("10");
        for (OrderFilledMessage incomplete : List.of(
                new OrderFilledMessage(null, 5, "BUY", ten, ten, NOW),
                new OrderFilledMessage(42, null, "BUY", ten, ten, NOW),
                new OrderFilledMessage(42, 5, null, ten, ten, NOW),
                new OrderFilledMessage(42, 5, "BUY", null, ten, NOW),
                new OrderFilledMessage(42, 5, "BUY", ten, null, NOW),
                new OrderFilledMessage(42, 5, "BUY", ten, ten, null))) {
            assertFalse(service.record(incomplete), incomplete.toString());
        }

        verify(stored, never()).save(any());
    }

    @Test
    void theWindowIsTheLastTwentyFourHours() {
        assertEquals(Instant.parse("2026-10-07T14:30:00Z"), service.windowStart());
    }

    @Test
    void activityIsWhatTheDatabaseSumsFromTheStartOfTheWindow() {
        Instant since = service.windowStart();
        List<InstrumentActivity> summed = List.of(
            new InstrumentActivity(5, 2, new BigDecimal("30"), new BigDecimal("7327.098"), NOW));
        when(stored.summarizeSince(since)).thenReturn(summed);

        assertSame(summed, service.activitySince(since));
    }
}
