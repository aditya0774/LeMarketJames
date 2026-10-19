package com.lemarketjames.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Latest persisted price for one instrument ({@code market_quotes}, one row per instrument).
 *
 * <p>Live prices are held in memory by the simulator; this row is refreshed every few seconds
 * so the market resumes from where it left off after a restart.
 *
 * <p>{@code lastUpdated} is stored as a UTC {@link LocalDateTime} because the existing column is
 * {@code TIMESTAMP} (without time zone).
 */
@Entity
@Table(name = "market_quotes")
public class MarketQuoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_id")
    private Integer quoteId;

    @Column(name = "instrument_id", nullable = false, unique = true)
    private Integer instrumentId;

    @Column(name = "bid_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal bidPrice;

    @Column(name = "ask_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal askPrice;

    @Column(name = "last_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "open_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "previous_close", nullable = false, precision = 14, scale = 4)
    private BigDecimal previousClose;

    @Column(nullable = false)
    private Long volume;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    public MarketQuoteEntity() {
    }

    public Integer getQuoteId() {
        return quoteId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(BigDecimal bidPrice) {
        this.bidPrice = bidPrice;
    }

    public BigDecimal getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(BigDecimal askPrice) {
        this.askPrice = askPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
