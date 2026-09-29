package com.lemarketjames.market.service;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.GbmModel;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

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
@Service
public class MarketSimulator implements MarketDataService, MarketFeedControl {

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

    /** Test controls (contract C4). Pinned instruments are skipped by tick; guarded by this object's lock. */
    private final Set<Integer> pinned = new HashSet<>();
    private volatile FeedMode feedMode = FeedMode.LIVE;
    /** What readers see while STALE: the quotes at the moment the feed went stale, backdated. */
    private volatile Map<Integer, QuoteSnapshot> staleSnapshotsById = Map.of();

    private final Set<LocalDate> holidays;
    private Instant lastTickAt;

    public MarketSimulator(MarketSimulationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.holidays = properties.holidaySet();
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
            if (pinned.contains(instrument.instrument().instrumentId())) {
                continue; // held at the price a test set
            }
            if (instrument.isTrading(now, properties.isRespectMarketHours(), holidays)) {
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
        return Optional.ofNullable(snapshotsByTicker.get(ticker.trim().toUpperCase(Locale.ROOT)))
                .map(live -> visible(live.instrument().instrumentId(), live));
    }

    @Override
    public Optional<QuoteSnapshot> findByInstrumentId(int instrumentId) {
        return Optional.ofNullable(snapshotsById.get(instrumentId)).map(live -> visible(instrumentId, live));
    }

    /** What quote readers see: the live quotes, or the frozen stale ones while the feed is STALE. */
    @Override
    public Collection<QuoteSnapshot> findAll() {
        return feedMode == FeedMode.STALE
                ? List.copyOf(staleSnapshotsById.values())
                : List.copyOf(snapshotsById.values());
    }

    /**
     * The real current prices whatever the feed mode, for persistence: a STALE feed must never
     * overwrite the stored prices with its backdated copies.
     */
    public Collection<QuoteSnapshot> latestSnapshots() {
        return List.copyOf(snapshotsById.values());
    }

    private QuoteSnapshot visible(int instrumentId, QuoteSnapshot live) {
        return feedMode == FeedMode.STALE ? staleSnapshotsById.getOrDefault(instrumentId, live) : live;
    }

    @Override
    public FeedMode feedMode() {
        return feedMode;
    }

    @Override
    public synchronized void setFeedMode(FeedMode mode) {
        if (mode == FeedMode.STALE && feedMode != FeedMode.STALE) {
            Duration age = properties.getControl().getStaleAge();
            Map<Integer, QuoteSnapshot> frozen = new HashMap<>();
            snapshotsById.forEach((id, quote) -> frozen.put(id, backdated(quote, age)));
            staleSnapshotsById = Map.copyOf(frozen);
        }
        feedMode = mode;
        log.info("Quote feed mode set to {}", mode);
    }

    @Override
    public synchronized void setPrice(String ticker, double price, boolean pin) {
        if (!(price > 0) || !Double.isFinite(price)) {
            throw new IllegalArgumentException("Price must be a positive number");
        }
        SimulatedInstrument instrument = simulated(ticker);
        instrument.overridePrice(price, clock.instant());
        int id = instrument.instrument().instrumentId();
        if (pin) {
            pinned.add(id);
        } else {
            pinned.remove(id);
        }
        publish(instrument);
        log.info("Price of {} set to {} (pinned={})", instrument.instrument().ticker(), price, pin);
    }

    @Override
    public synchronized void releasePrice(String ticker) {
        pinned.remove(simulated(ticker).instrument().instrumentId());
    }

    @Override
    public synchronized Set<String> pinnedTickers() {
        return pinned.stream().map(id -> instruments.get(id).instrument().ticker())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public synchronized void reset() {
        pinned.clear();
        setFeedMode(FeedMode.LIVE);
    }

    private SimulatedInstrument simulated(String ticker) {
        String wanted = ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
        return instruments.values().stream()
                .filter(instrument -> instrument.instrument().ticker().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ticker: " + ticker));
    }

    private static QuoteSnapshot backdated(QuoteSnapshot q, Duration age) {
        Instant updated = q.lastUpdated() == null ? null : q.lastUpdated().minus(age);
        return new QuoteSnapshot(q.instrument(), q.lastPrice(), q.bidPrice(), q.askPrice(), q.openPrice(),
                q.highPrice(), q.lowPrice(), q.previousClose(), q.volume(), updated, q.tradingDate());
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
