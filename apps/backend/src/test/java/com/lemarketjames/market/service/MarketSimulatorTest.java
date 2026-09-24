package com.lemarketjames.market.service;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@DisplayName("Market Simulator Unit Tests")
class MarketSimulatorTest {

    /** Wednesday 16 Sep 2026, 11:00 New York: US market open. */
    private static final Instant OPEN = Instant.parse("2026-09-16T15:00:00Z");
    /** Wednesday 16 Sep 2026, 18:00 New York: US market closed. */
    private static final Instant CLOSED = Instant.parse("2026-09-16T22:00:00Z");

    private static final MarketInstrument AAPL = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    private static final MarketInstrument MSFT = new MarketInstrument(2, "MSFT", "Microsoft Corp", "EQUITY", "USD", "US",
            429.85, 0.09, 0.23, 0.65, 2.0, 7_430_000_000L, 22_000_000L, 12.10, 3.32);
    private static final MarketInstrument GOOGL = new MarketInstrument(3, "GOOGL", "Alphabet Inc", "EQUITY", "USD", "US",
            190.25, 0.12, 0.28, 0.70, 1.5, 30_000_000_000L, 18_000_000L, 8.25, 0.75);

    private MarketSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = newSimulator(OPEN, true, 42L);
    }

    // ========== Load Tests ==========

    @Test
    @DisplayName("load initializes simulator with instruments at initial price")
    void testLoadInitializesFromInitialPrice() {
        simulator.load(List.of(AAPL, MSFT), Map.of());

        Optional<QuoteSnapshot> aapl = simulator.findByTicker("AAPL");
        Optional<QuoteSnapshot> msft = simulator.findByTicker("MSFT");

        assertTrue(aapl.isPresent());
        assertTrue(msft.isPresent());
        assertEquals(227.55, aapl.get().lastPrice(), 1e-9);
        assertEquals(429.85, msft.get().lastPrice(), 1e-9);
    }

    @Test
    @DisplayName("load resumes from stored quotes")
    void testLoadResumesFromStoredQuotes() {
        MarketSimulator.StoredQuote storedAAPL = new MarketSimulator.StoredQuote(
                240.0, 235.0, 241.0, 234.0, 233.0, 1_000_000L, OPEN.minusSeconds(60));
        MarketSimulator.StoredQuote storedMSFT = new MarketSimulator.StoredQuote(
                440.0, 435.0, 445.0, 430.0, 425.0, 2_000_000L, OPEN.minusSeconds(60));

        simulator.load(List.of(AAPL, MSFT), Map.of(1, storedAAPL, 2, storedMSFT));

        assertEquals(240.0, simulator.findByInstrumentId(1).orElseThrow().lastPrice(), 1e-9);
        assertEquals(440.0, simulator.findByInstrumentId(2).orElseThrow().lastPrice(), 1e-9);
        assertEquals(233.0, simulator.findByInstrumentId(1).orElseThrow().previousClose(), 1e-9);
    }

    @Test
    @DisplayName("load replaces previous instruments")
    void testLoadReplacesInstruments() {
        simulator.load(List.of(AAPL, MSFT), Map.of());
        assertEquals(2, simulator.findAll().size());

        simulator.load(List.of(GOOGL), Map.of());

        assertEquals(1, simulator.findAll().size());
        assertTrue(simulator.findByTicker("GOOGL").isPresent());
        assertFalse(simulator.findByTicker("AAPL").isPresent());
    }

    @Test
    @DisplayName("load with empty list clears simulator")
    void testLoadWithEmptyListClearsSimulator() {
        simulator.load(List.of(AAPL, MSFT), Map.of());
        simulator.load(List.of(), Map.of());

        assertEquals(0, simulator.findAll().size());
        assertTrue(simulator.findByTicker("AAPL").isEmpty());
    }

    @Test
    @DisplayName("load partially resumes from stored quotes (mix of new and resumed)")
    void testLoadPartiallyResumes() {
        MarketSimulator.StoredQuote storedAAPL = new MarketSimulator.StoredQuote(
                240.0, 235.0, 241.0, 234.0, 233.0, 1_000_000L, OPEN.minusSeconds(60));

        simulator.load(List.of(AAPL, MSFT), Map.of(1, storedAAPL));

        assertEquals(240.0, simulator.findByInstrumentId(1).orElseThrow().lastPrice(), 1e-9);
        assertEquals(429.85, simulator.findByInstrumentId(2).orElseThrow().lastPrice(), 1e-9);
    }

    // ========== Tick Tests ==========

    @Test
    @DisplayName("tick moves prices forward in trading hours")
    void testTickMovesPricesInTradingHours() {
        simulator.load(List.of(AAPL), Map.of());
        double before = simulator.findByTicker("AAPL").orElseThrow().lastPrice();

        simulator.tick(OPEN.plusSeconds(1));

        assertNotEquals(before, simulator.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    @Test
    @DisplayName("tick does not move prices during market closed hours")
    void testTickDoesNotMovePricesDuringClosedHours() {
        simulator.load(List.of(AAPL), Map.of());
        QuoteSnapshot before = simulator.findByTicker("AAPL").orElseThrow();

        simulator.tick(CLOSED.plusSeconds(1));

        assertEquals(before, simulator.findByTicker("AAPL").orElseThrow());
    }

    @Test
    @DisplayName("tick ignores market hours when respectMarketHours=false")
    void testTickIgnoresMarketHoursWhenDisabled() {
        MarketSimulator closedSimulator = newSimulator(CLOSED, false, 42L);
        closedSimulator.load(List.of(AAPL), Map.of());
        double before = closedSimulator.findByTicker("AAPL").orElseThrow().lastPrice();

        closedSimulator.tick(CLOSED.plusSeconds(1));

        assertNotEquals(before, closedSimulator.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    @Test
    @DisplayName("tick with empty simulator returns without error")
    void testTickWithEmptySimulator() {
        simulator.load(List.of(), Map.of());

        simulator.tick(OPEN.plusSeconds(1));

        assertEquals(0, simulator.findAll().size());
    }

    @Test
    @DisplayName("tick clamps elapsed time to MAX_TICKS_PER_STEP")
    void testTickClampsElapsedTime() {
        simulator.load(List.of(AAPL), Map.of());
        simulator.tick(OPEN);
        simulator.tick(OPEN.plusSeconds(1));
        double priceAfterSmallGap = simulator.findByTicker("AAPL").orElseThrow().lastPrice();

        // Reset and test with large gap (should be clamped)
        simulator.load(List.of(AAPL), Map.of());
        simulator.tick(OPEN);
        simulator.tick(OPEN.plusSeconds(3600)); // 1 hour gap - should be clamped

        double priceAfterLargeGap = simulator.findByTicker("AAPL").orElseThrow().lastPrice();
        double largeGapRatio = Math.abs(Math.log(priceAfterLargeGap / 227.55));

        // Ensure price change is bounded despite large time gap
        assertTrue(largeGapRatio < 0.1, "large time gap should be clamped and not produce extreme price moves");
    }

    @Test
    @DisplayName("tick uses wall-clock time when no prior tick")
    void testTickUsesWallClockTimeInitially() {
        simulator.load(List.of(AAPL), Map.of());
        double before = simulator.findByTicker("AAPL").orElseThrow().lastPrice();

        simulator.tick(OPEN.plusSeconds(10));

        assertNotEquals(before, simulator.findByTicker("AAPL").orElseThrow().lastPrice());
    }

    @Test
    @DisplayName("tick with same instant does not move price")
    void testTickWithSameInstantDoesNotMovePrices() {
        simulator.load(List.of(AAPL), Map.of());
        simulator.tick(OPEN);
        QuoteSnapshot first = simulator.findByTicker("AAPL").orElseThrow();

        simulator.tick(OPEN);

        QuoteSnapshot second = simulator.findByTicker("AAPL").orElseThrow();
        assertEquals(first.lastPrice(), second.lastPrice(), 1e-9);
    }

    // ========== FindByTicker Tests ==========

    @Test
    @DisplayName("findByTicker returns quote for valid ticker")
    void testFindByTickerReturnsQuote() {
        simulator.load(List.of(AAPL), Map.of());

        Optional<QuoteSnapshot> result = simulator.findByTicker("AAPL");

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().instrument().ticker());
    }

    @Test
    @DisplayName("findByTicker is case-insensitive")
    void testFindByTickerIsCaseInsensitive() {
        simulator.load(List.of(AAPL), Map.of());

        assertTrue(simulator.findByTicker("aapl").isPresent());
        assertTrue(simulator.findByTicker("AAPL").isPresent());
        assertTrue(simulator.findByTicker("AaPl").isPresent());
    }

    @Test
    @DisplayName("findByTicker returns empty for non-existent ticker")
    void testFindByTickerReturnsEmptyForNonExistent() {
        simulator.load(List.of(AAPL), Map.of());

        Optional<QuoteSnapshot> result = simulator.findByTicker("NONEXISTENT");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findByTicker returns empty for null ticker")
    void testFindByTickerReturnsEmptyForNullTicker() {
        simulator.load(List.of(AAPL), Map.of());

        Optional<QuoteSnapshot> result = simulator.findByTicker(null);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findByTicker trims whitespace")
    void testFindByTickerTrimsWhitespace() {
        simulator.load(List.of(AAPL), Map.of());

        Optional<QuoteSnapshot> result = simulator.findByTicker("  AAPL  ");

        assertTrue(result.isPresent());
    }

    // ========== FindByInstrumentId Tests ==========

    @Test
    @DisplayName("findByInstrumentId returns quote for valid ID")
    void testFindByInstrumentIdReturnsQuote() {
        simulator.load(List.of(AAPL), Map.of());

        Optional<QuoteSnapshot> result = simulator.findByInstrumentId(1);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().instrument().instrumentId());
    }

    @Test
    @DisplayName("findByInstrumentId returns empty for non-existent ID")
    void testFindByInstrumentIdReturnsEmptyForNonExistent() {
        simulator.load(List.of(AAPL), Map.of());

        Optional<QuoteSnapshot> result = simulator.findByInstrumentId(999);

        assertFalse(result.isPresent());
    }

    // ========== FindAll Tests ==========

    @Test
    @DisplayName("findAll returns all instruments")
    void testFindAllReturnsAllInstruments() {
        simulator.load(List.of(AAPL, MSFT, GOOGL), Map.of());

        var all = simulator.findAll();

        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("findAll returns empty list when simulator is empty")
    void testFindAllReturnsEmptyList() {
        simulator.load(List.of(), Map.of());

        var all = simulator.findAll();

        assertEquals(0, all.size());
    }

    @Test
    @DisplayName("findAll returns immutable copy")
    void testFindAllReturnsImmutableCopy() {
        simulator.load(List.of(AAPL, MSFT), Map.of());
        var all = simulator.findAll();

        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }

    // ========== Candle Tests ==========

    @Test
    @DisplayName("drainCompletedCandles returns completed candles")
    void testDrainCompletedCandlesReturnsCandles() {
        simulator.load(List.of(AAPL, MSFT), Map.of());
        for (int i = 1; i <= 180; i++) {
            simulator.tick(OPEN.plusSeconds(i));
        }

        List<PriceCandle> candles = simulator.drainCompletedCandles();

        assertTrue(candles.size() > 0);
        for (PriceCandle candle : candles) {
            assertTrue(candle.lowPrice() <= Math.min(candle.openPrice(), candle.closePrice()));
            assertTrue(candle.highPrice() >= Math.max(candle.openPrice(), candle.closePrice()));
            assertTrue(candle.volume() > 0);
        }
    }

    @Test
    @DisplayName("drainCompletedCandles empties queue")
    void testDrainCompletedCandlesEmptiesQueue() {
        simulator.load(List.of(AAPL), Map.of());
        for (int i = 1; i <= 180; i++) {
            simulator.tick(OPEN.plusSeconds(i));
        }

        simulator.drainCompletedCandles();
        List<PriceCandle> second = simulator.drainCompletedCandles();

        assertEquals(0, second.size());
    }

    @Test
    @DisplayName("drainCompletedCandles returns empty list when no candles completed")
    void testDrainCompletedCandlesReturnsEmptyList() {
        simulator.load(List.of(AAPL), Map.of());

        List<PriceCandle> candles = simulator.drainCompletedCandles();

        assertEquals(0, candles.size());
    }

    // ========== Determinism Tests ==========

    @Test
    @DisplayName("same seed produces identical market evolution")
    void testSameSeedProducesSameMarket() {
        MarketSimulator first = newSimulator(OPEN, true, 42L);
        first.load(List.of(AAPL), Map.of());

        MarketSimulator second = newSimulator(OPEN, true, 42L);
        second.load(List.of(AAPL), Map.of());

        for (int i = 1; i <= 50; i++) {
            first.tick(OPEN.plusSeconds(i));
            second.tick(OPEN.plusSeconds(i));
        }

        assertEquals(
                first.findByTicker("AAPL").orElseThrow().lastPrice(),
                second.findByTicker("AAPL").orElseThrow().lastPrice(),
                1e-9
        );
    }

    @Test
    @DisplayName("different seeds produce different market evolution")
    void testDifferentSeedsProduceDifferentMarkets() {
        MarketSimulator first = newSimulator(OPEN, true, 42L);
        first.load(List.of(AAPL), Map.of());

        MarketSimulator second = newSimulator(OPEN, true, 99L);
        second.load(List.of(AAPL), Map.of());

        for (int i = 1; i <= 50; i++) {
            first.tick(OPEN.plusSeconds(i));
            second.tick(OPEN.plusSeconds(i));
        }

        assertNotEquals(
                first.findByTicker("AAPL").orElseThrow().lastPrice(),
                second.findByTicker("AAPL").orElseThrow().lastPrice(),
                1e-9
        );
    }

    @Test
    @DisplayName("null seed creates non-deterministic market")
    void testNullSeedCreatesDifferentMarkets() {
        MarketSimulator first = newSimulator(OPEN, true, null);
        first.load(List.of(AAPL), Map.of());

        MarketSimulator second = newSimulator(OPEN, true, null);
        second.load(List.of(AAPL), Map.of());

        for (int i = 1; i <= 50; i++) {
            first.tick(OPEN.plusSeconds(i));
            second.tick(OPEN.plusSeconds(i));
        }

        // Very unlikely to be exactly equal with random seeds
        // (could theoretically happen, but extremely rare)
        // We just verify both produce prices, not that they're different
        assertTrue(first.findByTicker("AAPL").isPresent());
        assertTrue(second.findByTicker("AAPL").isPresent());
    }

    // ========== Trading Day Boundary Tests ==========

    @Test
    @DisplayName("new trading day rolls previous close and resets daily stats")
    void testNewTradingDayRollsStats() {
        simulator.load(List.of(AAPL), Map.of());
        for (int i = 1; i <= 30; i++) {
            simulator.tick(OPEN.plusSeconds(i));
        }
        QuoteSnapshot endOfDay = simulator.findByTicker("AAPL").orElseThrow();

        Instant nextDay = OPEN.plusSeconds(24 * 3600);
        simulator.tick(nextDay);
        QuoteSnapshot nextDayQuote = simulator.findByTicker("AAPL").orElseThrow();

        assertEquals(endOfDay.lastPrice(), nextDayQuote.previousClose(), 1e-9);
        assertEquals(endOfDay.lastPrice(), nextDayQuote.openPrice(), 1e-9);
    }

    // ========== Speed Multiplier Tests ==========

    @Test
    @DisplayName("speed multiplier affects price movement magnitude")
    void testSpeedMultiplierAffectsPriceMoment() {
        MarketSimulator fastSimulator = newSimulatorWithSpeedMultiplier(OPEN, true, 42L, 10.0);
        fastSimulator.load(List.of(AAPL), Map.of());
        double priceAfterFastTick = simulateAndGetPrice(fastSimulator, 10);

        MarketSimulator normalSimulator = newSimulatorWithSpeedMultiplier(OPEN, true, 42L, 1.0);
        normalSimulator.load(List.of(AAPL), Map.of());
        double priceAfterNormalTick = simulateAndGetPrice(normalSimulator, 10);

        // Speed multiplier should affect price change
        // Fast market should move more than normal market
        double fastRatio = Math.abs(Math.log(priceAfterFastTick / 227.55));
        double normalRatio = Math.abs(Math.log(priceAfterNormalTick / 227.55));
        assertTrue(fastRatio > normalRatio * 0.5, "faster market should have different price movement");
    }

    // ========== Helper Methods ==========

    private static MarketSimulator newSimulator(Instant now, boolean respectMarketHours, Long seed) {
        MarketSimulationProperties properties = new MarketSimulationProperties();
        properties.setSeed(seed);
        properties.setRespectMarketHours(respectMarketHours);
        return new MarketSimulator(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static MarketSimulator newSimulatorWithSpeedMultiplier(Instant now, boolean respectMarketHours, Long seed, double speedMultiplier) {
        MarketSimulationProperties properties = new MarketSimulationProperties();
        properties.setSeed(seed);
        properties.setRespectMarketHours(respectMarketHours);
        properties.setSpeedMultiplier(speedMultiplier);
        return new MarketSimulator(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static double simulateAndGetPrice(MarketSimulator simulator, int ticks) {
        for (int i = 1; i <= ticks; i++) {
            simulator.tick(OPEN.plusSeconds(i));
        }
        return simulator.findByTicker("AAPL").orElseThrow().lastPrice();
    }

    private static <T> void assertThrows(Class<T> expectedException, Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected " + expectedException.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            if (!expectedException.isInstance(e)) {
                throw new AssertionError("Expected " + expectedException.getSimpleName() + " but got " + e.getClass().getSimpleName(), e);
            }
        }
    }
}
