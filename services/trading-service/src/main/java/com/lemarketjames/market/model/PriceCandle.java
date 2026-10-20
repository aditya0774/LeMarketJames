package com.lemarketjames.market.model;

import java.time.Instant;

/**
 * A completed 1-minute OHLC (open/high/low/close) bar, ready to be stored in {@code price_candles}.
 *
 * @param instrumentId  instrument the candle belongs to
 * @param intervalStart UTC start of the minute
 * @param openPrice     first price in the minute
 * @param highPrice     highest price in the minute
 * @param lowPrice      lowest price in the minute
 * @param closePrice    last price in the minute
 * @param volume        volume traded in the minute
 */
public record PriceCandle(
        int instrumentId,
        Instant intervalStart,
        double openPrice,
        double highPrice,
        double lowPrice,
        double closePrice,
        long volume) {
}
