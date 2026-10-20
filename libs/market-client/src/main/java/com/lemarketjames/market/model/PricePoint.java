package com.lemarketjames.market.model;

import java.time.Instant;

/**
 * An instrument's price at one moment of the current trading day: the close of a 1-minute candle,
 * or the latest live price.
 *
 * @param time  when the price applied (UTC)
 * @param price the price
 */
public record PricePoint(Instant time, double price) {
}
