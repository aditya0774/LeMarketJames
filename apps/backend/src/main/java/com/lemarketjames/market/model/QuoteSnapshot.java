package com.lemarketjames.market.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable point-in-time view of an instrument's price. The simulator replaces the whole
 * snapshot on every tick, so readers on other threads never see a half-updated quote.
 *
 * @param instrument    the instrument this quote belongs to
 * @param lastPrice     latest (mid) price
 * @param bidPrice      price a seller would receive
 * @param askPrice      price a buyer would pay
 * @param openPrice     first price of the current trading day
 * @param highPrice     highest price of the current trading day
 * @param lowPrice      lowest price of the current trading day
 * @param previousClose last price of the previous trading day
 * @param volume        shares traded so far in the current trading day
 * @param lastUpdated   when this price was produced
 * @param tradingDate   exchange-local date of the trading day this quote belongs to
 */
public record QuoteSnapshot(
        MarketInstrument instrument,
        double lastPrice,
        double bidPrice,
        double askPrice,
        double openPrice,
        double highPrice,
        double lowPrice,
        double previousClose,
        long volume,
        Instant lastUpdated,
        LocalDate tradingDate) {

    /** @return absolute change since the previous close */
    public double priceChange() {
        return lastPrice - previousClose;
    }

    /** @return percentage change since the previous close, e.g. 1.25 for +1.25% */
    public double priceChangePercent() {
        return previousClose == 0 ? 0 : (lastPrice - previousClose) / previousClose * 100;
    }

    /** @return market capitalisation, or 0 when shares outstanding is not applicable */
    public long marketCap() {
        Long shares = instrument.sharesOutstanding();
        return shares == null ? 0L : Math.round(lastPrice * shares);
    }

    /** @return price / earnings ratio, or 0 when earnings are unknown or not positive */
    public double peRatio() {
        Double eps = instrument.earningsPerShare();
        return eps == null || eps <= 0 ? 0 : lastPrice / eps;
    }

    /** @return annual dividend yield as a percentage, e.g. 0.44 for 0.44% */
    public double dividendYieldPercent() {
        return lastPrice == 0 ? 0 : instrument.dividendPerShare() / lastPrice * 100;
    }
}
