package com.lemarketjames.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.math.BigDecimal;

/**
 * The open, high, low, close and volume columns that every stored price bar has, whatever span it
 * covers: a minute in {@code price_candles}, a trading day in {@code price_history}. Each subclass
 * adds its own table, key and time column.
 */
@MappedSuperclass
public abstract class OhlcBar {

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

    protected OhlcBar() {
    }

    protected OhlcBar(BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                      Long volume) {
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
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
}
