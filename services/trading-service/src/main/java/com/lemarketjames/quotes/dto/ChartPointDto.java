package com.lemarketjames.quotes.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One point on a price chart.
 *
 * @param time  when the price applied (UTC); a daily close is stamped at the exchange's closing time
 * @param price the price, rounded to 2 decimals like every quote
 */
public record ChartPointDto(Instant time, BigDecimal price) {
}
