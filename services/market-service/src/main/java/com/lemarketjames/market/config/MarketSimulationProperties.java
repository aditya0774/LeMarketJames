package com.lemarketjames.market.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable settings for the market simulator, bound from {@code sim.*} properties.
 *
 * <p>Defaults live here (not only in {@code application.properties}) because the test
 * classpath ships its own {@code application.properties} that replaces the main one.
 */
@Validated
@ConfigurationProperties(prefix = "sim")
public class MarketSimulationProperties {

    /** Whether prices tick automatically. Tests disable this so results are deterministic. */
    private boolean enabled = true;

    /** Milliseconds between simulation ticks. */
    @Positive
    private long tickMs = 1000;

    /** Random seed. Set it to replay the exact same market; leave empty for a different market each run. */
    private Long seed;

    /**
     * How many simulated seconds pass per real second. 1 = real time. Larger values make
     * prices move faster, which is useful for demos.
     */
    @Positive
    private double speedMultiplier = 1.0;

    /** Milliseconds between writes of the latest prices and completed candles to the database. */
    @Positive
    private long snapshotIntervalMs = 5000;

    /**
     * When true, instruments only tick while their exchange is open (e.g. US equities
     * 09:30-16:00 New York time, weekdays). When false, every instrument ticks around the clock,
     * which is convenient for development outside market hours.
     */
    private boolean respectMarketHours = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTickMs() {
        return tickMs;
    }

    public void setTickMs(long tickMs) {
        this.tickMs = tickMs;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public long getSnapshotIntervalMs() {
        return snapshotIntervalMs;
    }

    public void setSnapshotIntervalMs(long snapshotIntervalMs) {
        this.snapshotIntervalMs = snapshotIntervalMs;
    }

    public boolean isRespectMarketHours() {
        return respectMarketHours;
    }

    public void setRespectMarketHours(boolean respectMarketHours) {
        this.respectMarketHours = respectMarketHours;
    }
}
