package com.lemarketjames.market.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketHoursTest {

    @Test
    void usEquityOpenOnlyDuringRegularSessionNewYorkTime() {
        MarketHours hours = MarketHours.US_EQUITY;

        // Wednesday 16 Sep 2026; New York is UTC-4 (daylight saving time).
        assertFalse(hours.isOpen(Instant.parse("2026-09-16T13:29:59Z")), "09:29:59 NY is before the open");
        assertTrue(hours.isOpen(Instant.parse("2026-09-16T13:30:00Z")), "09:30 NY is the open");
        assertTrue(hours.isOpen(Instant.parse("2026-09-16T19:59:59Z")), "15:59:59 NY is still open");
        assertFalse(hours.isOpen(Instant.parse("2026-09-16T20:00:00Z")), "16:00 NY is the close");
    }

    @Test
    void usEquityClosedAtWeekends() {
        assertFalse(MarketHours.US_EQUITY.isOpen(Instant.parse("2026-09-19T15:00:00Z")), "Saturday");
        assertFalse(MarketHours.US_EQUITY.isOpen(Instant.parse("2026-09-20T15:00:00Z")), "Sunday");
    }

    @Test
    void holidaysCloseTheWholeExchangeLocalDay() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 26));

        assertFalse(MarketHours.US_EQUITY.isOpen(Instant.parse("2026-11-26T16:00:00Z"), holidays), "Thanksgiving");
        assertTrue(MarketHours.US_EQUITY.isOpen(Instant.parse("2026-11-27T16:00:00Z"), holidays), "the day after");
    }

    @Test
    void quoteFreshnessComparesAgeWithTheLimit() {
        Instant now = Instant.parse("2026-09-16T15:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        assertFalse(QuoteFreshness.isStale(quoteAt(now.minusSeconds(60)), Duration.ofSeconds(60), clock));
        assertTrue(QuoteFreshness.isStale(quoteAt(now.minusSeconds(61)), Duration.ofSeconds(60), clock));
        assertTrue(QuoteFreshness.isStale(quoteAt(null), Duration.ofSeconds(60), clock), "no timestamp is stale");
    }

    private static QuoteSnapshot quoteAt(Instant lastUpdated) {
        return new QuoteSnapshot(null, 1, 1, 1, 1, 1, 1, 1, 0, lastUpdated, null);
    }

    @Test
    void cryptoIsAlwaysOpen() {
        assertTrue(MarketHours.CRYPTO.isOpen(Instant.parse("2026-09-20T03:00:00Z")));
    }

    @Test
    void tradingDateUsesExchangeTimeZone() {
        // 02:00 UTC on the 17th is still the evening of the 16th in New York.
        assertEquals(LocalDate.of(2026, 9, 16), MarketHours.US_EQUITY.tradingDate(Instant.parse("2026-09-17T02:00:00Z")));
    }

    @Test
    void tradingYearLengthMatchesSessionLength() {
        assertEquals(252 * 6.5 * 3600, MarketHours.US_EQUITY.tradingSecondsPerYear(), 1e-9);
        assertEquals(365 * 24 * 3600, MarketHours.CRYPTO.tradingSecondsPerYear(), 1e-9);
    }

    @Test
    void picksHoursFromAssetClassAndLocation() {
        assertSame(MarketHours.US_EQUITY, MarketHours.forInstrument("EQUITY", "US"));
        assertSame(MarketHours.UK_EQUITY, MarketHours.forInstrument("EQUITY", "UK"));
        assertSame(MarketHours.INDIA_EQUITY, MarketHours.forInstrument("EQUITY", "INDIA"));
        assertSame(MarketHours.FOREX, MarketHours.forInstrument("FOREX", null));
        assertSame(MarketHours.CRYPTO, MarketHours.forInstrument("CRYPTO", null));
        assertSame(MarketHours.US_EQUITY, MarketHours.forInstrument("EQUITY", null));
    }
}
