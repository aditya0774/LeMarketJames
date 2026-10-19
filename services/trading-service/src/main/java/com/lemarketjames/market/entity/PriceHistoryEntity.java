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
 * exchange-local trading date. Seeded with six months of history; trading-service writes the current
 * day's bar every time it saves prices.
 */
@Entity
@Table(name = "price_history")
@IdClass(PriceHistoryEntity.Key.class)
public class PriceHistoryEntity extends OhlcBar {

    @Id
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Id
    @Column(name = "trading_date")
    private LocalDate tradingDate;

    public PriceHistoryEntity() {
    }

    public PriceHistoryEntity(Integer instrumentId, LocalDate tradingDate, BigDecimal openPrice,
                              BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice, Long volume) {
        super(openPrice, highPrice, lowPrice, closePrice, volume);
        this.instrumentId = instrumentId;
        this.tradingDate = tradingDate;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getTradingDate() {
        return tradingDate;
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
