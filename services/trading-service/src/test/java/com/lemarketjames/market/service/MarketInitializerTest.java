package com.lemarketjames.market.service;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.MarketInstrument;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketInitializerTest {

    /** Wednesday 16 Sep 2026, 10:41 New York (EDT, UTC-4). */
    private static final Instant NOW = Instant.parse("2026-09-16T14:41:00Z");
    /** 09:30 New York that day. */
    private static final Instant OPEN = Instant.parse("2026-09-16T13:30:00Z");

    private static final MarketInstrument AAPL = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    private static final MarketInstrument MSFT = new MarketInstrument(2, "MSFT", "Microsoft Corp", "EQUITY", "USD", "US",
            429.85, 0.09, 0.23, 0.65, 2.0, 7_430_000_000L, 22_000_000L, 12.10, 3.32);

    private final MarketPersistenceService persistence = mock(MarketPersistenceService.class);
    private final MarketSimulator simulator = mock(MarketSimulator.class);

    @Test
    void fillsFromTheOpenWhenNothingIsStoredAndFromTheLastCandleOtherwise() {
        Map<Integer, Instant> from = MarketInitializer.catchUpFrom(List.of(AAPL, MSFT),
                Map.of(2, Instant.parse("2026-09-16T14:00:00Z")), NOW, Set.of());

        assertEquals(Map.of(1, OPEN, 2, Instant.parse("2026-09-16T14:01:00Z")), from);
    }

    @Test
    void nothingToFillBeforeTheOpenOnAWeekendOrOnAHoliday() {
        assertEquals(Map.of(), MarketInitializer.catchUpFrom(List.of(AAPL), Map.of(),
                Instant.parse("2026-09-16T12:00:00Z"), Set.of()), "08:00 New York");
        assertEquals(Map.of(), MarketInitializer.catchUpFrom(List.of(AAPL), Map.of(),
                Instant.parse("2026-09-19T15:00:00Z"), Set.of()), "Saturday");
        assertEquals(Map.of(), MarketInitializer.catchUpFrom(List.of(AAPL), Map.of(),
                NOW, Set.of(LocalDate.of(2026, 9, 16))), "holiday");
    }

    @Test
    void nothingToFillWhenTheLatestCandleIsCurrent() {
        assertEquals(Map.of(), MarketInitializer.catchUpFrom(List.of(AAPL),
                Map.of(1, Instant.parse("2026-09-16T14:40:00Z")), NOW, Set.of()));
    }

    @Test
    void catchesUpAndSavesStraightAway() {
        when(persistence.loadInstruments()).thenReturn(List.of(AAPL));
        when(persistence.loadStoredQuotes()).thenReturn(Map.of());
        when(persistence.latestCandleStarts(OPEN)).thenReturn(Map.of());

        initializer(new MarketSimulationProperties()).run(null);

        verify(simulator).load(List.of(AAPL), Map.of(), Map.of(1, OPEN));
        verify(persistence).save(any(), anyList());
    }

    @Test
    void skipsTheCatchUpWhenTickingIsOffHoursAreIgnoredOrItIsSwitchedOff() {
        when(persistence.loadInstruments()).thenReturn(List.of(AAPL));
        when(persistence.loadStoredQuotes()).thenReturn(Map.of());
        MarketSimulationProperties noTicking = new MarketSimulationProperties();
        noTicking.setEnabled(false);
        MarketSimulationProperties noHours = new MarketSimulationProperties();
        noHours.setRespectMarketHours(false);
        MarketSimulationProperties switchedOff = new MarketSimulationProperties();
        switchedOff.setCatchUpOnStart(false);

        for (MarketSimulationProperties properties : List.of(noTicking, noHours, switchedOff)) {
            initializer(properties).run(null);
        }

        verify(simulator, times(3)).load(eq(List.of(AAPL)), eq(Map.of()), eq(Map.of()));
        verify(persistence, never()).latestCandleStarts(any());
        verify(persistence, never()).save(any(), anyList());
    }

    private MarketInitializer initializer(MarketSimulationProperties properties) {
        return new MarketInitializer(persistence, simulator, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
