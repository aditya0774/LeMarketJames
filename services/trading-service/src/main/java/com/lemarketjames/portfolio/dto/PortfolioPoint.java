package com.lemarketjames.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The value of an account's stocks at one moment.
 *
 * @param time  the moment (UTC); a daily point is stamped at the exchange's closing time
 * @param value what the stocks held then were worth, in USD
 */
public record PortfolioPoint(Instant time, BigDecimal value) {
}
