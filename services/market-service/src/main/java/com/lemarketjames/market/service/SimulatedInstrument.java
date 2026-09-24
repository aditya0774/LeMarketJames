package com.lemarketjames.market.service;

import com.lemarketjames.market.model.GbmModel;
import com.lemarketjames.market.model.MarketHours;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Mutable price state for one instrument. Only ever touched by the simulator's tick, under the
 * simulator's lock; other threads read the immutable {@link QuoteSnapshot} it publishes.
 */
class SimulatedInstrument {

    /**
     * Typical volume noise: per-tick volume is multiplied by a lognormal factor with this sigma.
     * The factor's mean is corrected back to 1 so average daily volume stays as configured.
     */
    private static final double VOLUME_NOISE_SIGMA = 0.5;

    private final MarketInstrument instrument;
    private final MarketHours hours;

    private double lastPrice;
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double previousClose;
    private long volume;
    private Instant lastUpdated;
    private LocalDate tradingDate;

    // Candle currently being built; completed when the minute changes.
    private Instant candleStart;
    private double candleOpen;
    private double candleHigh;
    private double candleLow;
    private double candleClose;
    private long candleVolume;

    /** Starts from the configured initial price, as if at the open of a new day. */
    SimulatedInstrument(MarketInstrument instrument, Instant now) {
        this(instrument, instrument.initialPrice(), instrument.initialPrice(), instrument.initialPrice(),
                instrument.initialPrice(), instrument.initialPrice(), 0L, now);
    }

    /** Resumes from previously stored prices, e.g. after a restart. */
    SimulatedInstrument(MarketInstrument instrument, double lastPrice, double openPrice, double highPrice,
                        double lowPrice, double previousClose, long volume, Instant lastUpdated) {
        this.instrument = instrument;
        this.hours = instrument.marketHours();
        this.lastPrice = lastPrice;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.previousClose = previousClose;
        this.volume = volume;
        this.lastUpdated = lastUpdated;
        this.tradingDate = hours.tradingDate(lastUpdated);
    }

    MarketInstrument instrument() {
        return instrument;
    }

    /**
     * Whether this instrument's price may move at {@code now}.
     *
     * @param respectMarketHours when false, the market is treated as always open
     */
    boolean isTrading(Instant now, boolean respectMarketHours) {
        return !respectMarketHours || hours.isOpen(now);
    }

    /**
     * Applies one GBM step.
     *
     * @param now              time of this tick
     * @param elapsedSeconds   simulated seconds since the previous tick (already speed-adjusted)
     * @param shock            correlated standard normal shock for this instrument
     * @param volumeNoise      independent standard normal draw for volume
     * @return a candle if this tick started a new minute and the previous one is complete
     */
    Optional<PriceCandle> advance(Instant now, double elapsedSeconds, double shock, double volumeNoise) {
        rollTradingDayIfNeeded(now);

        double dtYears = elapsedSeconds / hours.tradingSecondsPerYear();
        double priceBeforeTick = lastPrice;
        lastPrice = GbmModel.nextPrice(lastPrice, instrument.drift(), instrument.volatility(), dtYears, shock);
        highPrice = Math.max(highPrice, lastPrice);
        lowPrice = Math.min(lowPrice, lastPrice);

        long tickVolume = simulateVolume(elapsedSeconds, shock, volumeNoise);
        volume += tickVolume;
        lastUpdated = now;

        Optional<PriceCandle> completed = completeCandleIfMinuteChanged(now);
        if (candleStart == null) {
            startCandle(now, priceBeforeTick);
        }
        candleHigh = Math.max(candleHigh, lastPrice);
        candleLow = Math.min(candleLow, lastPrice);
        candleClose = lastPrice;
        candleVolume += tickVolume;
        return completed;
    }

    /**
     * Closes out the in-progress candle once its minute has passed, even while the market is
     * closed, so the last candle of a session is stored promptly rather than at the next open.
     */
    Optional<PriceCandle> completeCandleIfMinuteChanged(Instant now) {
        boolean sameMinute = candleStart != null && candleStart.equals(now.truncatedTo(ChronoUnit.MINUTES));
        if (candleStart == null || sameMinute) {
            return Optional.empty();
        }
        PriceCandle candle = new PriceCandle(instrument.instrumentId(), candleStart,
                candleOpen, candleHigh, candleLow, candleClose, candleVolume);
        candleStart = null;
        return Optional.of(candle);
    }

    QuoteSnapshot snapshot() {
        // Half the spread either side of the mid price.
        double halfSpread = lastPrice * instrument.spreadBps() / 10_000 / 2;
        return new QuoteSnapshot(instrument, lastPrice, lastPrice - halfSpread, lastPrice + halfSpread,
                openPrice, highPrice, lowPrice, previousClose, volume, lastUpdated, tradingDate);
    }

    /** At the first tick of a new trading day, yesterday's last price becomes the previous close. */
    private void rollTradingDayIfNeeded(Instant now) {
        LocalDate today = hours.tradingDate(now);
        if (today.equals(tradingDate)) {
            return;
        }
        previousClose = lastPrice;
        openPrice = lastPrice;
        highPrice = lastPrice;
        lowPrice = lastPrice;
        volume = 0;
        tradingDate = today;
    }

    /**
     * Volume is not part of GBM, so it is approximated: the configured average daily volume
     * spread evenly across the session, with random noise, and heavier on ticks with large
     * price moves (as real markets trade more on big moves).
     */
    private long simulateVolume(double elapsedSeconds, double shock, double volumeNoise) {
        double expected = instrument.avgDailyVolume() * elapsedSeconds / hours.sessionSeconds();
        double noise = Math.exp(VOLUME_NOISE_SIGMA * volumeNoise - 0.5 * VOLUME_NOISE_SIGMA * VOLUME_NOISE_SIGMA);
        // E|Z| is about 0.8, so 0.6 + 0.5|Z| averages roughly 1.
        double moveFactor = 0.6 + 0.5 * Math.abs(shock);
        return Math.max(0L, Math.round(expected * noise * moveFactor));
    }

    private void startCandle(Instant now, double openingPrice) {
        candleStart = now.truncatedTo(ChronoUnit.MINUTES);
        candleOpen = openingPrice;
        candleHigh = openingPrice;
        candleLow = openingPrice;
        candleClose = openingPrice;
        candleVolume = 0;
    }
}
