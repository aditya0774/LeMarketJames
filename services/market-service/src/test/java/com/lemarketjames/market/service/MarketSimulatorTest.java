package com.lemarketjames.market.service;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSimulatorTest {

    /** Wednesday 16 Sep 2026, 11:00 New York: US market open. */
    private static final Instant OPEN = Instant.parse("2026-09-16T15:00:00Z");
    /** Wednesday 16 Sep 2026, 18:00 New York: US market closed. */
    private static final Instant CLOSED = Instant.parse("2026-09-16T22:00:00Z");

    private static final MarketInstrument AAPL = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    private static final MarketInstrument MSFT = new MarketInstrument(2, "MSFT", "Microsoft Corp", "EQUITY", "USD", "US",
            429.85, 0.09, 0.23, 0.65, 2.0, 7_430_000_000L, 22_000_000L, 12.10, 3.32);

    @Test
    void startsFromInitialPriceWhenNothingIsStored() {
        MarketSimulator simulator = simulator(OPEN, true);

        QuoteSnapshot quote = simulator.findByTicker("aapl").orElseThrow();

        assertEquals(227.55, quote.lastPrice(), 1e-9);
        assertEquals(227.55, quote.previousClose(), 1e-9);
        assertTrue(quote.bidPrice() < quote.lastPrice() && quote.lastPrice() < quote.askPrice());
    }

    @Test
    void resumesFromStoredQuote() {
        MarketSimulator simulator = newSimulator(OPEN, true, 42L);
        MarketSimulator.StoredQuote stored = new MarketSimulator.StoredQuote(
                240.0, 235.0, 241.0, 234.0, 233.0, 1_000_000L, OPEN.minusSeconds(60));

        simulator.load(List.of(AAPL), Map.of(1, stored));

        QuoteSnapshot quote = simulator.findByInstrumentId(1).orElseThrow();
        assertEquals(240.0, quote.lastPrice(), 1e-9);
        assertEquals(233.0, quote.previousClose(), 1e-9);
        assertEquals(1_000_000L, quote.volume());
    }

    @Test
    void pricesMoveOnlyWhenTicked() {
        MarketSimulator simulator = simulator(OPEN, true);
        double before = simulator.findByTicker("AAPL").orElseThrow().lastPrice();

        assertEquals(before, simulator.findByTicker("AAPL").orElseThrow().lastPrice(), "reading must not move price");

        simulator.tick(OPEN.plusSeconds(1));
        assertNotEquals(before, simulator.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    @Test
    void pricesDoNotMoveWhileMarketIsClosed() {
        MarketSimulator simulator = simulator(CLOSED, true);
        QuoteSnapshot before = simulator.findByTicker("AAPL").orElseThrow();

        simulator.tick(CLOSED.plusSeconds(1));

        assertEquals(before, simulator.findByTicker("AAPL").orElseThrow());
    }

    @Test
    void pricesMoveOutsideMarketHoursWhenHoursAreIgnored() {
        MarketSimulator simulator = simulator(CLOSED, false);
        double before = simulator.findByTicker("AAPL").orElseThrow().lastPrice();

        simulator.tick(CLOSED.plusSeconds(1));

        assertNotEquals(before, simulator.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    @Test
    void sameSeedProducesSameMarket() {
        MarketSimulator first = simulator(OPEN, true);
        MarketSimulator second = simulator(OPEN, true);

        for (int i = 1; i <= 100; i++) {
            first.tick(OPEN.plusSeconds(i));
            second.tick(OPEN.plusSeconds(i));
        }

        assertEquals(first.findByTicker("AAPL").orElseThrow().lastPrice(),
                second.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    @Test
    void newTradingDayRollsPreviousCloseAndResetsDailyStats() {
        MarketSimulator simulator = simulator(OPEN, true);
        for (int i = 1; i <= 30; i++) {
            simulator.tick(OPEN.plusSeconds(i));
        }
        QuoteSnapshot endOfDay = simulator.findByTicker("AAPL").orElseThrow();

        // Next trading day, shortly after the open.
        Instant nextDay = OPEN.plusSeconds(24 * 3600);
        simulator.tick(nextDay);
        QuoteSnapshot nextDayQuote = simulator.findByTicker("AAPL").orElseThrow();

        assertEquals(endOfDay.lastPrice(), nextDayQuote.previousClose(), 1e-9);
        assertEquals(endOfDay.lastPrice(), nextDayQuote.openPrice(), 1e-9);
        assertTrue(nextDayQuote.volume() < endOfDay.volume(), "volume restarts each day");
    }

    @Test
    void completesOneCandlePerMinuteWithConsistentPrices() {
        MarketSimulator simulator = simulator(OPEN, true);
        for (int i = 1; i <= 180; i++) {
            simulator.tick(OPEN.plusSeconds(i));
        }

        List<PriceCandle> candles = simulator.drainCompletedCandles();

        // Ticks span 15:00:01-15:03:00, so minutes 15:00, 15:01 and 15:02 are complete for each instrument.
        assertEquals(3 * 2, candles.size());
        for (PriceCandle candle : candles) {
            assertTrue(candle.lowPrice() <= Math.min(candle.openPrice(), candle.closePrice()));
            assertTrue(candle.highPrice() >= Math.max(candle.openPrice(), candle.closePrice()));
            assertTrue(candle.volume() > 0);
        }
        assertTrue(simulator.drainCompletedCandles().isEmpty(), "draining removes candles");
    }

    @Test
    void stallProtectionCapsHowFarOneTickCanMove() {
        // A tick an hour after the last one is treated as at most 5 ticks of simulated time,
        // so the price cannot jump by an hour's worth of volatility in one step.
        MarketSimulator simulator = simulator(OPEN, true);
        simulator.tick(OPEN.plusSeconds(3600));

        double price = simulator.findByTicker("AAPL").orElseThrow().lastPrice();
        assertTrue(Math.abs(Math.log(price / 227.55)) < 0.01);
    }

    @Test
    void pricesDoNotMoveOnAConfiguredHoliday() {
        // Thursday 26 Nov 2026 (Thanksgiving, in the default holiday list), 11:00 New York.
        Instant thanksgiving = Instant.parse("2026-11-26T16:00:00Z");
        MarketSimulator simulator = simulator(thanksgiving, true);
        QuoteSnapshot before = simulator.findByTicker("AAPL").orElseThrow();

        simulator.tick(thanksgiving.plusSeconds(1));

        assertEquals(before, simulator.findByTicker("AAPL").orElseThrow());
    }

    // Contract C4: a test can set a stock's price and hold it there while the rest of the market moves.
    @Test
    void pinnedPriceHoldsThroughTicks() {
        MarketSimulator simulator = simulator(OPEN, true);
        double msftBefore = simulator.findByTicker("MSFT").orElseThrow().lastPrice();

        simulator.setPrice("aapl", 100.0, true);
        simulator.tick(OPEN.plusSeconds(1));

        assertEquals(100.0, simulator.findByTicker("AAPL").orElseThrow().lastPrice(), 1e-9);
        assertNotEquals(msftBefore, simulator.findByTicker("MSFT").orElseThrow().lastPrice());
        assertEquals(Set.of("AAPL"), simulator.pinnedTickers());

        simulator.releasePrice("AAPL");
        simulator.tick(OPEN.plusSeconds(2));
        assertNotEquals(100.0, simulator.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    // A held price is still a live quote: it must stay inside the staleness limit (contract C5),
    // or orders on a pinned stock would be refused as stale.
    @Test
    void pinnedPriceKeepsAFreshTimestampWhileTheMarketIsOpen() {
        MarketSimulator simulator = simulator(OPEN, true);
        simulator.setPrice("AAPL", 100.0, true);

        simulator.tick(OPEN.plusSeconds(30));

        QuoteSnapshot held = simulator.findByTicker("AAPL").orElseThrow();
        assertEquals(100.0, held.lastPrice(), 1e-9);
        assertEquals(OPEN.plusSeconds(30), held.lastUpdated());
    }

    @Test
    void pinnedPriceIsNotRestampedWhileTheMarketIsClosed() {
        MarketSimulator simulator = simulator(CLOSED, true);
        simulator.setPrice("AAPL", 100.0, true);
        QuoteSnapshot before = simulator.findByTicker("AAPL").orElseThrow();

        simulator.tick(CLOSED.plusSeconds(30));

        assertEquals(before, simulator.findByTicker("AAPL").orElseThrow());
    }

    @Test
    void unpinnedPriceMovesOnFromTheSetValue() {
        MarketSimulator simulator = simulator(OPEN, true);

        simulator.setPrice("AAPL", 100.0, false);
        simulator.tick(OPEN.plusSeconds(1));

        double moved = simulator.findByTicker("AAPL").orElseThrow().lastPrice();
        assertNotEquals(100.0, moved);
        assertTrue(Math.abs(moved - 100.0) < 1.0, "moves from 100, not from the old price");
    }

    // Contract C4: STALE makes readers see old quotes at once, without corrupting what gets persisted.
    @Test
    void staleFeedBackdatesQuotesButNotPersistedPrices() {
        MarketSimulator simulator = simulator(OPEN, true);
        simulator.setFeedMode(FeedMode.STALE);
        simulator.tick(OPEN.plusSeconds(1));

        QuoteSnapshot seen = simulator.findByTicker("AAPL").orElseThrow();
        assertEquals(OPEN.minus(Duration.ofMinutes(10)), seen.lastUpdated());
        assertEquals(227.55, seen.lastPrice(), 1e-9, "frozen at the moment the feed went stale");
        QuoteSnapshot stored = simulator.latestSnapshots().stream()
                .filter(q -> q.instrument().ticker().equals("AAPL")).findFirst().orElseThrow();
        assertEquals(OPEN.plusSeconds(1), stored.lastUpdated());

        simulator.reset();
        assertEquals(FeedMode.LIVE, simulator.feedMode());
        assertEquals(stored, simulator.findByTicker("AAPL").orElseThrow());
    }

    @Test
    void settingThePriceOfAnUnknownTickerFails() {
        MarketSimulator simulator = simulator(OPEN, true);

        assertThrows(IllegalArgumentException.class, () -> simulator.setPrice("NOPE", 10.0, true));
        assertThrows(IllegalArgumentException.class, () -> simulator.setPrice("AAPL", -1.0, true));
    }

    private static MarketSimulator simulator(Instant now, boolean respectMarketHours) {
        MarketSimulator simulator = newSimulator(now, respectMarketHours, 42L);
        simulator.load(List.of(AAPL, MSFT), Map.of());
        return simulator;
    }

    private static MarketSimulator newSimulator(Instant now, boolean respectMarketHours, Long seed) {
        MarketSimulationProperties properties = new MarketSimulationProperties();
        properties.setSeed(seed);
        properties.setRespectMarketHours(respectMarketHours);
        return new MarketSimulator(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
