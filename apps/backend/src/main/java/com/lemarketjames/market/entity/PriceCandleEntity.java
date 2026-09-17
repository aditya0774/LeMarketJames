package com.lemarketjames.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One persisted 1-minute OHLC bar ({@code price_candles}, migration 006), keyed by instrument
 * and UTC interval start.
 */
@Entity
@Table(name = "price_candles")
@IdClass(PriceCandleEntity.Key.class)
public class PriceCandleEntity {

    @Id
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Id
    @Column(name = "interval_start")
    private LocalDateTime intervalStart;

    @Column(name = "open_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private Long volume;

    public PriceCandleEntity() {
    }

    public PriceCandleEntity(Integer instrumentId, LocalDateTime intervalStart, BigDecimal openPrice,
                             BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice, Long volume) {
        this.instrumentId = instrumentId;
        this.intervalStart = intervalStart;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public LocalDateTime getIntervalStart() {
        return intervalStart;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public Long getVolume() {
        return volume;
    }

    /** Composite primary key: (instrument_id, interval_start). */
    public static class Key implements Serializable {

        private Integer instrumentId;
        private LocalDateTime intervalStart;

        public Key() {
        }

        public Key(Integer instrumentId, LocalDateTime intervalStart) {
            this.instrumentId = instrumentId;
            this.intervalStart = intervalStart;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(instrumentId, key.instrumentId) && Objects.equals(intervalStart, key.intervalStart);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentId, intervalStart);
        }
    }
}
