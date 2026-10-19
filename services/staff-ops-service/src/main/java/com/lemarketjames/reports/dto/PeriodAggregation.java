package com.lemarketjames.reports.dto;

import java.math.BigDecimal;

/**
 * A single aggregated period in a trade report.
 * Contains trade counts (total, buy, sell) and aggregate value for the period.
 */
public class PeriodAggregation {

    private String period;
    private int tradeCount;
    private int buyCount;
    private int sellCount;
    private BigDecimal totalValue;

    public PeriodAggregation() {
    }

    public PeriodAggregation(String period, int tradeCount, int buyCount, int sellCount, BigDecimal totalValue) {
        this.period = period;
        this.tradeCount = tradeCount;
        this.buyCount = buyCount;
        this.sellCount = sellCount;
        this.totalValue = totalValue;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(int tradeCount) {
        this.tradeCount = tradeCount;
    }

    public int getBuyCount() {
        return buyCount;
    }

    public void setBuyCount(int buyCount) {
        this.buyCount = buyCount;
    }

    public int getSellCount() {
        return sellCount;
    }

    public void setSellCount(int sellCount) {
        this.sellCount = sellCount;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }
}
