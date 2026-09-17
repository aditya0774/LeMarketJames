package com.lemarketjames.market.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the market clock: ticks prices and periodically saves them.
 *
 * <p>Only created when {@code sim.enabled} is true. Spring's default scheduler is single
 * threaded, so a tick and a save never run at the same time.
 */
@Component
@ConditionalOnProperty(prefix = "sim", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketScheduler.class);

    private final MarketSimulator simulator;
    private final MarketPersistenceService persistence;

    public MarketScheduler(MarketSimulator simulator, MarketPersistenceService persistence) {
        this.simulator = simulator;
        this.persistence = persistence;
    }

    @Scheduled(fixedRateString = "${sim.tick-ms:1000}")
    public void tick() {
        simulator.tick();
    }

    @Scheduled(fixedRateString = "${sim.snapshot-interval-ms:5000}",
            initialDelayString = "${sim.snapshot-interval-ms:5000}")
    public void saveSnapshot() {
        try {
            persistence.save(simulator.findAll(), simulator.drainCompletedCandles());
        } catch (RuntimeException ex) {
            // A failed save must not stop the market; the next save writes the latest prices again.
            // Candles drained in this attempt are lost, which only leaves a gap in chart history.
            log.error("Failed to persist market snapshot", ex);
        }
    }
}
