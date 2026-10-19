package com.lemarketjames.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One trading day's OHLC bar ({@code price_history}, migration 022), keyed by instrument and
 * exchange-local trading date. Seeded with six months of history; market-service writes the current
 * day's bar every time it saves prices.
 */
@Entity
@Table(name = "price_history")
@IdClass(PriceHistoryEntity.Key.class)
public class PriceHistoryEntity {

    @Id
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Id
    @Column(name = "trading_date")
    private LocalDate tradingDate;

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

    public PriceHistoryEntity() {
    }

    public PriceHistoryEntity(Integer instrumentId, LocalDate tradingDate, BigDecimal openPrice,
                              BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice, Long volume) {
        this.instrumentId = instrumentId;
        this.tradingDate = tradingDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getTradingDate() {
        return tradingDate;
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

    /** Composite primary key: (instrument_id, trading_date). */
    public static class Key implements Serializable {

        private Integer instrumentId;
        private LocalDate tradingDate;

        public Key() {
        }

        public Key(Integer instrumentId, LocalDate tradingDate) {
            this.instrumentId = instrumentId;
            this.tradingDate = tradingDate;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(instrumentId, key.instrumentId) && Objects.equals(tradingDate, key.tradingDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentId, tradingDate);
        }
    }
}
