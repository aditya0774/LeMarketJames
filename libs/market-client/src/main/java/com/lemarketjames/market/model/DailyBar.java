package com.lemarketjames.market.model;

import java.time.LocalDate;

/**
 * One trading day of an instrument's price ({@code price_history}, migration 022).
 *
 * @param date   exchange-local trading day
 * @param open   first price of the day
 * @param high   highest price of the day
 * @param low    lowest price of the day
 * @param close  last price of the day; for today, the latest price so far
 * @param volume shares traded that day
 */
public record DailyBar(LocalDate date, double open, double high, double low, double close, long volume) {
}
