package com.lemarketjames.market.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads instruments and their last stored prices into the simulator on startup.
 *
 * <p>Runs whether or not ticking is enabled, so quotes are always available (tests rely on this
 * with {@code sim.enabled=false}).
 */
@Component
public class MarketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketInitializer.class);

    private final MarketPersistenceService persistence;
    private final MarketSimulator simulator;

    public MarketInitializer(MarketPersistenceService persistence, MarketSimulator simulator) {
        this.persistence = persistence;
        this.simulator = simulator;
    }

    @Override
    public void run(ApplicationArguments args) {
        var instruments = persistence.loadInstruments();
        if (instruments.isEmpty()) {
            // Most likely migration 006 has not been applied to this database.
            log.warn("No instruments have market simulation parameters; quotes will return 404. "
                    + "Has database/schema/006_market_simulation.sql been applied?");
        }
        simulator.load(instruments, persistence.loadStoredQuotes());
    }
}
