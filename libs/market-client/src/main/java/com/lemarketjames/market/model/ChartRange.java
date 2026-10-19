package com.lemarketjames.market.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Optional;

/**
 * The time spans a price chart can show, shared by the stock chart (core-service) and the portfolio
 * chart (holdings-service) so both accept the same {@code range} values. One day is drawn from
 * today's 1-minute candles; every longer span from daily bars.
 */
public enum ChartRange {

    ONE_DAY("1D", Period.ZERO),
    ONE_WEEK("1W", Period.ofWeeks(1)),
    ONE_MONTH("1M", Period.ofMonths(1)),
    THREE_MONTHS("3M", Period.ofMonths(3)),
    SIX_MONTHS("6M", Period.ofMonths(6));

    private final String code;
    private final Period span;

    ChartRange(String code, Period span) {
        this.code = code;
        this.span = span;
    }

    /** @return the value used in the {@code range} query parameter, e.g. "1M" */
    public String code() {
        return code;
    }

    /** @return whether this range is drawn from today's minute prices rather than daily bars */
    public boolean intraday() {
        return span.isZero();
    }

    /** @return the first trading day this range covers, counted back from {@code today} */
    public LocalDate startDate(LocalDate today) {
        return today.minus(span);
    }

    /** @return the range with this code, ignoring case; empty for an unknown code */
    public static Optional<ChartRange> fromCode(String code) {
        return Arrays.stream(values()).filter(range -> range.code.equalsIgnoreCase(code)).findFirst();
    }
}
