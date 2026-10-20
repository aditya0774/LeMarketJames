package com.lemarketjames.market.service;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.MarketHours;
import com.lemarketjames.market.model.MarketInstrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads instruments and their last stored prices into the simulator on startup, and fills in the
 * part of today's session the service missed while it was down.
 *
 * <p>Runs whether or not ticking is enabled, so quotes are always available (tests rely on this
 * with {@code sim.enabled=false}). The catch-up only runs while ticking is enabled
 * ({@code sim.catch-up-on-start}, see {@link MarketSimulationProperties}).
 */
@Component
public class MarketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketInitializer.class);

    private static final Duration CANDLE = Duration.ofMinutes(1);

    private final MarketPersistenceService persistence;
    private final MarketSimulator simulator;
    private final MarketSimulationProperties properties;
    private final Clock clock;

    public MarketInitializer(MarketPersistenceService persistence, MarketSimulator simulator,
                             MarketSimulationProperties properties, Clock clock) {
        this.persistence = persistence;
        this.simulator = simulator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        var instruments = persistence.loadInstruments();
        if (instruments.isEmpty()) {
            // Most likely migration 006 has not been applied to this database.
            log.warn("No instruments have market simulation parameters; quotes will return 404. "
                    + "Has database/schema/006_market_simulation.sql been applied?");
        }
        Map<Integer, Instant> catchUpFrom = catchUpEnabled() ? gaps(instruments) : Map.of();
        simulator.load(instruments, persistence.loadStoredQuotes(), catchUpFrom);
        if (!catchUpFrom.isEmpty()) {
            // Saved now rather than at the first snapshot, so the day's charts have it straight away.
            persistence.save(simulator.latestSnapshots(), simulator.drainCompletedCandles());
        }
    }

    /** Without market hours every stock trades around the clock, so there is no session to fill. */
    private boolean catchUpEnabled() {
        return properties.isEnabled() && properties.isCatchUpOnStart() && properties.isRespectMarketHours();
    }

    private Map<Integer, Instant> gaps(List<MarketInstrument> instruments) {
        Instant now = clock.instant();
        Instant earliestOpen = instruments.stream()
                .map(instrument -> todaysOpening(instrument.marketHours(), now))
                .min(Instant::compareTo)
                .orElse(now);
        return catchUpFrom(instruments, persistence.latestCandleStarts(earliestOpen), now, properties.holidaySet());
    }

    /**
     * Where each instrument's missed trading time starts: today's open, or the end of its latest
     * stored candle if that is later. Keyed on stored candles rather than the stored quote's time,
     * since a fresh database seeds quotes stamped at its creation but no candles. Instruments with
     * nothing to fill (not a trading day, not open yet, or already up to date) are absent.
     * Package-private for unit testing.
     *
     * @param latestCandleStarts per instrument id, the start of its latest stored candle today
     */
    static Map<Integer, Instant> catchUpFrom(List<MarketInstrument> instruments, Map<Integer, Instant> latestCandleStarts,
                                             Instant now, Set<LocalDate> holidays) {
        Map<Integer, Instant> from = new HashMap<>();
        for (MarketInstrument instrument : instruments) {
            MarketHours hours = instrument.marketHours();
            Instant open = todaysOpening(hours, now);
            // Open at the opening instant means today is a trading day (not a weekend or holiday).
            if (!hours.isOpen(open, holidays) || !open.isBefore(now)) {
                continue;
            }
            Instant start = open;
            Instant latest = latestCandleStarts.get(instrument.instrumentId());
            if (latest != null && latest.plus(CANDLE).isAfter(start)) {
                start = latest.plus(CANDLE);
            }
            if (start.isBefore(now)) {
                from.put(instrument.instrumentId(), start);
            }
        }
        return from;
    }

    private static Instant todaysOpening(MarketHours hours, Instant now) {
        return hours.openingTime(hours.tradingDate(now));
    }
}
