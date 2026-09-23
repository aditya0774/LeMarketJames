package com.lemarketjames.market.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Trading hours for a market, used to decide when an instrument's price may move and how long
 * a "trading year" is for the GBM time step.
 *
 * <p>Deliberately simple: exchange holidays and half days are not modelled.
 *
 * @param zone               time zone the exchange operates in
 * @param open               local opening time
 * @param close              local closing time
 * @param weekdaysOnly       whether the market is closed on Saturday and Sunday
 * @param tradingDaysPerYear trading days per year, used to annualise volatility
 */
public record MarketHours(ZoneId zone, LocalTime open, LocalTime close, boolean weekdaysOnly, int tradingDaysPerYear) {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    /** NYSE / NASDAQ regular session. */
    public static final MarketHours US_EQUITY =
            new MarketHours(NEW_YORK, LocalTime.of(9, 30), LocalTime.of(16, 0), true, 252);

    /** London Stock Exchange regular session. */
    public static final MarketHours UK_EQUITY =
            new MarketHours(ZoneId.of("Europe/London"), LocalTime.of(8, 0), LocalTime.of(16, 30), true, 252);

    /** National Stock Exchange of India regular session. */
    public static final MarketHours INDIA_EQUITY =
            new MarketHours(ZoneId.of("Asia/Kolkata"), LocalTime.of(9, 15), LocalTime.of(15, 30), true, 250);

    /** Foreign exchange, approximated as 24 hours on weekdays (New York time). */
    public static final MarketHours FOREX =
            new MarketHours(NEW_YORK, LocalTime.MIN, LocalTime.MAX, true, 260);

    /** Crypto trades continuously, every day of the year. */
    public static final MarketHours CRYPTO =
            new MarketHours(ZoneOffset.UTC, LocalTime.MIN, LocalTime.MAX, false, 365);

    /**
     * Picks the trading hours for an instrument.
     *
     * @param assetClass value of {@code instruments.asset_class} (EQUITY, FOREX, CRYPTO)
     * @param location   value of {@code instruments.location} (US, UK, INDIA); may be null
     * @return matching hours, defaulting to US equity hours for unknown equity locations
     */
    public static MarketHours forInstrument(String assetClass, String location) {
        if ("CRYPTO".equalsIgnoreCase(assetClass)) {
            return CRYPTO;
        }
        if ("FOREX".equalsIgnoreCase(assetClass)) {
            return FOREX;
        }
        if ("UK".equalsIgnoreCase(location)) {
            return UK_EQUITY;
        }
        if ("INDIA".equalsIgnoreCase(location)) {
            return INDIA_EQUITY;
        }
        return US_EQUITY;
    }

    /** @return whether the market is open at the given instant */
    public boolean isOpen(Instant instant) {
        ZonedDateTime local = instant.atZone(zone);
        DayOfWeek day = local.getDayOfWeek();
        if (weekdaysOnly && (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return !time.isBefore(open) && time.isBefore(close);
    }

    /** @return the exchange-local calendar date, used to detect when a new trading day starts */
    public LocalDate tradingDate(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    /** @return length of one trading session in seconds */
    public double sessionSeconds() {
        // LocalTime.MAX is 23:59:59.999..., so treat "open all day" as a full 24 hours.
        if (open.equals(LocalTime.MIN) && close.equals(LocalTime.MAX)) {
            return 24 * 3600;
        }
        return close.toSecondOfDay() - open.toSecondOfDay();
    }

    /**
     * Seconds of trading in one year. Volatility and drift are quoted per year, so a tick of
     * {@code n} seconds is {@code n / tradingSecondsPerYear()} years of GBM time.
     */
    public double tradingSecondsPerYear() {
        return tradingDaysPerYear * sessionSeconds();
    }
}
