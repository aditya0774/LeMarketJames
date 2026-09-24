package com.lemarketjames.market.service;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.GbmModel;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.random.RandomGenerator;

/**
 * In-memory stock market driven by Geometric Brownian Motion.
 *
 * <p>Prices move on a clock ({@link #tick()}, called by {@link MarketScheduler}), never as a side
 * effect of someone reading a quote. Each tick draws one market-wide shock plus one shock per
 * instrument and blends them ({@link GbmModel#correlatedShock}) so stocks tend to move together.
 *
 * <p>Threading: {@code tick} and {@code load} are synchronized and are the only writers. Readers
 * get immutable {@link QuoteSnapshot}s from concurrent maps and never block.
 */
@Primary
@Service
public class MarketSimulator implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketSimulator.class);

    /**
     * Cap on how much simulated time one tick may cover. Protects against a single huge price
     * jump if the scheduler stalls (e.g. a long GC pause or a laptop waking from sleep).
     */
    private static final int MAX_TICKS_PER_STEP = 5;

    private final MarketSimulationProperties properties;
    private final Clock clock;
    private final RandomGenerator random;

    /**
     * Mutable state, only accessed while holding this object's lock. Insertion-ordered so a fixed
     * seed always applies random draws to instruments in the same order.
     */
    private final Map<Integer, SimulatedInstrument> instruments = new LinkedHashMap<>();

    private final Map<Integer, QuoteSnapshot> snapshotsById = new ConcurrentHashMap<>();
    private final Map<String, QuoteSnapshot> snapshotsByTicker = new ConcurrentHashMap<>();
    private final Queue<PriceCandle> completedCandles = new ConcurrentLinkedQueue<>();

    private Instant lastTickAt;

    public MarketSimulator(MarketSimulationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // A fixed seed replays exactly the same market, which is useful for demos and bug reports.
        this.random = properties.getSeed() != null
                ? new SplittableRandom(properties.getSeed())
                : new SplittableRandom();
    }

    /**
     * Replaces the simulated universe.
     *
     * @param definitions instruments to simulate
     * @param stored      last persisted quotes by instrument id; instruments without one start
     *                    from their configured initial price
     */
    public synchronized void load(List<MarketInstrument> definitions, Map<Integer, StoredQuote> stored) {
        Instant now = clock.instant();
        instruments.clear();
        snapshotsById.clear();
        snapshotsByTicker.clear();

        for (MarketInstrument definition : definitions) {
            StoredQuote saved = stored.get(definition.instrumentId());
            SimulatedInstrument simulated = saved == null
                    ? new SimulatedInstrument(definition, now)
                    : new SimulatedInstrument(definition, saved.lastPrice(), saved.openPrice(), saved.highPrice(),
                            saved.lowPrice(), saved.previousClose(), saved.volume(), saved.lastUpdated());
            instruments.put(definition.instrumentId(), simulated);
            publish(simulated);
        }
        lastTickAt = now;
        log.info("Market simulator loaded {} instruments ({} resumed from stored quotes)",
                definitions.size(), stored.size());
    }

    /** Advances every open instrument to the current clock time. */
    public void tick() {
        tick(clock.instant());
    }

    /**
     * Advances every open instrument to {@code now}. Exposed with an explicit time so tests can
     * drive the market deterministically.
     */
    public synchronized void tick(Instant now) {
        if (instruments.isEmpty()) {
            return;
        }
        double elapsedSeconds = elapsedSimulatedSeconds(now);
        lastTickAt = now;

        // One shared draw per tick is what makes instruments move together.
        double marketShock = random.nextGaussian();
        for (SimulatedInstrument instrument : instruments.values()) {
            if (instrument.isTrading(now, properties.isRespectMarketHours())) {
                double shock = GbmModel.correlatedShock(
                        marketShock, random.nextGaussian(), instrument.instrument().marketCorrelation());
                instrument.advance(now, elapsedSeconds, shock, random.nextGaussian())
                        .ifPresent(completedCandles::add);
                publish(instrument);
            } else {
                instrument.completeCandleIfMinuteChanged(now).ifPresent(completedCandles::add);
            }
        }
    }

    /**
     * Removes and returns candles completed since the last call, for persistence.
     */
    public List<PriceCandle> drainCompletedCandles() {
        List<PriceCandle> drained = new ArrayList<>();
        PriceCandle candle;
        while ((candle = completedCandles.poll()) != null) {
            drained.add(candle);
        }
        return drained;
    }

    @Override
    public Optional<QuoteSnapshot> findByTicker(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshotsByTicker.get(ticker.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public Optional<QuoteSnapshot> findByInstrumentId(int instrumentId) {
        return Optional.ofNullable(snapshotsById.get(instrumentId));
    }

    @Override
    public Collection<QuoteSnapshot> findAll() {
        return List.copyOf(snapshotsById.values());
    }

    private void publish(SimulatedInstrument instrument) {
        QuoteSnapshot snapshot = instrument.snapshot();
        snapshotsById.put(snapshot.instrument().instrumentId(), snapshot);
        snapshotsByTicker.put(snapshot.instrument().ticker().toUpperCase(Locale.ROOT), snapshot);
    }

    private double elapsedSimulatedSeconds(Instant now) {
        double tickSeconds = properties.getTickMs() / 1000.0;
        double realSeconds = lastTickAt == null ? tickSeconds : (now.toEpochMilli() - lastTickAt.toEpochMilli()) / 1000.0;
        double bounded = Math.max(0, Math.min(realSeconds, tickSeconds * MAX_TICKS_PER_STEP));
        return bounded * properties.getSpeedMultiplier();
    }

    /**
     * Previously persisted price state used to resume the market after a restart.
     *
     * @param lastPrice     last price
     * @param openPrice     open of the stored trading day
     * @param highPrice     high of the stored trading day
     * @param lowPrice      low of the stored trading day
     * @param previousClose close of the day before the stored trading day
     * @param volume        volume of the stored trading day
     * @param lastUpdated   when the stored price was produced
     */
    public record StoredQuote(double lastPrice, double openPrice, double highPrice, double lowPrice,
                              double previousClose, long volume, Instant lastUpdated) {
    }
}
