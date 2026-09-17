package com.lemarketjames.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Simulation settings for one instrument ({@code instrument_market_params}, migration 006).
 *
 * <p>Linked to {@code instruments} by a plain id rather than a JPA relationship so the market
 * feature does not depend on the orders feature's {@code Instrument} entity.
 */
@Entity
@Table(name = "instrument_market_params")
public class InstrumentMarketParamsEntity {

    @Id
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Column(name = "initial_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal initialPrice;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal drift;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal volatility;

    @Column(name = "market_correlation", nullable = false, precision = 5, scale = 4)
    private BigDecimal marketCorrelation;

    @Column(name = "spread_bps", nullable = false, precision = 8, scale = 4)
    private BigDecimal spreadBps;

    @Column(name = "shares_outstanding")
    private Long sharesOutstanding;

    @Column(name = "avg_daily_volume", nullable = false)
    private Long avgDailyVolume;

    @Column(name = "earnings_per_share", precision = 14, scale = 4)
    private BigDecimal earningsPerShare;

    @Column(name = "dividend_per_share", nullable = false, precision = 14, scale = 4)
    private BigDecimal dividendPerShare;

    public InstrumentMarketParamsEntity() {
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getInitialPrice() {
        return initialPrice;
    }

    public void setInitialPrice(BigDecimal initialPrice) {
        this.initialPrice = initialPrice;
    }

    public BigDecimal getDrift() {
        return drift;
    }

    public void setDrift(BigDecimal drift) {
        this.drift = drift;
    }

    public BigDecimal getVolatility() {
        return volatility;
    }

    public void setVolatility(BigDecimal volatility) {
        this.volatility = volatility;
    }

    public BigDecimal getMarketCorrelation() {
        return marketCorrelation;
    }

    public void setMarketCorrelation(BigDecimal marketCorrelation) {
        this.marketCorrelation = marketCorrelation;
    }

    public BigDecimal getSpreadBps() {
        return spreadBps;
    }

    public void setSpreadBps(BigDecimal spreadBps) {
        this.spreadBps = spreadBps;
    }

    public Long getSharesOutstanding() {
        return sharesOutstanding;
    }

    public void setSharesOutstanding(Long sharesOutstanding) {
        this.sharesOutstanding = sharesOutstanding;
    }

    public Long getAvgDailyVolume() {
        return avgDailyVolume;
    }

    public void setAvgDailyVolume(Long avgDailyVolume) {
        this.avgDailyVolume = avgDailyVolume;
    }

    public BigDecimal getEarningsPerShare() {
        return earningsPerShare;
    }

    public void setEarningsPerShare(BigDecimal earningsPerShare) {
        this.earningsPerShare = earningsPerShare;
    }

    public BigDecimal getDividendPerShare() {
        return dividendPerShare;
    }

    public void setDividendPerShare(BigDecimal dividendPerShare) {
        this.dividendPerShare = dividendPerShare;
    }
}
