package com.lemarketjames.market.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The two stored price bars: their shared OHLC columns, and the composite keys Hibernate uses to
 * tell rows apart (so equal keys must be equal and hash alike).
 */
class PriceBarEntitiesTest {

    private static final BigDecimal OPEN = new BigDecimal("10.0000");
    private static final BigDecimal HIGH = new BigDecimal("12.0000");
    private static final BigDecimal LOW = new BigDecimal("9.5000");
    private static final BigDecimal CLOSE = new BigDecimal("11.0000");

    @Test
    void dailyBarCarriesItsDayAndPrices() {
        LocalDate day = LocalDate.of(2026, 10, 15);
        PriceHistoryEntity bar = new PriceHistoryEntity(7, day, OPEN, HIGH, LOW, CLOSE, 500L);

        assertEquals(7, bar.getInstrumentId());
        assertEquals(day, bar.getTradingDate());
        assertOhlc(bar);
    }

    @Test
    void minuteCandleCarriesItsMinuteAndPrices() {
        LocalDateTime minute = LocalDateTime.of(2026, 10, 15, 13, 30);
        PriceCandleEntity candle = new PriceCandleEntity(7, minute, OPEN, HIGH, LOW, CLOSE, 500L);

        assertEquals(7, candle.getInstrumentId());
        assertEquals(minute, candle.getIntervalStart());
        assertOhlc(candle);
    }

    @Test
    void dailyKeysAreEqualForTheSameInstrumentAndDay() {
        LocalDate day = LocalDate.of(2026, 10, 15);
        PriceHistoryEntity.Key key = new PriceHistoryEntity.Key(7, day);

        assertEquals(key, key);
        assertEquals(new PriceHistoryEntity.Key(7, day), key);
        assertEquals(new PriceHistoryEntity.Key(7, day).hashCode(), key.hashCode());
        assertNotEquals(new PriceHistoryEntity.Key(8, day), key);
        assertNotEquals(new PriceHistoryEntity.Key(7, day.plusDays(1)), key);
        // Anything that is not a key is never equal to one.
        Object notAKey = "7:2026-10-15";
        assertFalse(key.equals(notAKey));
        assertEquals(new PriceHistoryEntity.Key(), new PriceHistoryEntity.Key());
    }

    @Test
    void candleKeysAreEqualForTheSameInstrumentAndMinute() {
        LocalDateTime minute = LocalDateTime.of(2026, 10, 15, 13, 30);
        PriceCandleEntity.Key key = new PriceCandleEntity.Key(7, minute);

        assertEquals(key, key);
        assertEquals(new PriceCandleEntity.Key(7, minute), key);
        assertEquals(new PriceCandleEntity.Key(7, minute).hashCode(), key.hashCode());
        assertNotEquals(new PriceCandleEntity.Key(8, minute), key);
        assertNotEquals(new PriceCandleEntity.Key(7, minute.plusMinutes(1)), key);
        Object nothing = null;
        assertFalse(key.equals(nothing));
        assertEquals(new PriceCandleEntity.Key(), new PriceCandleEntity.Key());
    }

    private static void assertOhlc(OhlcBar bar) {
        assertEquals(OPEN, bar.getOpenPrice());
        assertEquals(HIGH, bar.getHighPrice());
        assertEquals(LOW, bar.getLowPrice());
        assertEquals(CLOSE, bar.getClosePrice());
        assertEquals(500L, bar.getVolume());
    }
}
