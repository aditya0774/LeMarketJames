package com.lemarketjames.portfolio.dto;

import java.math.BigDecimal;

/** Matches the balance shape documented in API-CONTRACTS.md's GET /api/v1/portfolio contract. */
public class PortfolioBalance {

    private final BigDecimal cash;
    private final BigDecimal invested;
    private final BigDecimal totalValue;
    private final BigDecimal buyingPower;
    private final BigDecimal dayGainLoss;
    private final BigDecimal dayGainLossPercent;
    private final BigDecimal totalGainLoss;
    private final BigDecimal totalGainLossPercent;
    private final String currency;

    public PortfolioBalance(BigDecimal cash, BigDecimal invested, BigDecimal totalValue, BigDecimal buyingPower,
                             BigDecimal dayGainLoss, BigDecimal dayGainLossPercent,
                             BigDecimal totalGainLoss, BigDecimal totalGainLossPercent, String currency) {
        this.cash = cash;
        this.invested = invested;
        this.totalValue = totalValue;
        this.buyingPower = buyingPower;
        this.dayGainLoss = dayGainLoss;
        this.dayGainLossPercent = dayGainLossPercent;
        this.totalGainLoss = totalGainLoss;
        this.totalGainLossPercent = totalGainLossPercent;
        this.currency = currency;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public BigDecimal getInvested() {
        return invested;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getBuyingPower() {
        return buyingPower;
    }

    public BigDecimal getDayGainLoss() {
        return dayGainLoss;
    }

    public BigDecimal getDayGainLossPercent() {
        return dayGainLossPercent;
    }

    public BigDecimal getTotalGainLoss() {
        return totalGainLoss;
    }

    public BigDecimal getTotalGainLossPercent() {
        return totalGainLossPercent;
    }

    public String getCurrency() {
        return currency;
    }
}
