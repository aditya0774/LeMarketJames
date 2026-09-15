package com.lemarketjames.holdings.dto;

import java.math.BigDecimal;

public class HoldingDto {
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private BigDecimal totalCost;
    private BigDecimal currentValue;
    private BigDecimal gainLoss;
    private BigDecimal gainLossPercent;

    public HoldingDto() {}

    public HoldingDto(String symbol, BigDecimal quantity, BigDecimal averageCost,
                      BigDecimal currentPrice, BigDecimal totalCost, BigDecimal currentValue,
                      BigDecimal gainLoss, BigDecimal gainLossPercent) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
        this.currentPrice = currentPrice;
        this.totalCost = totalCost;
        this.currentValue = currentValue;
        this.gainLoss = gainLoss;
        this.gainLossPercent = gainLossPercent;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getGainLoss() {
        return gainLoss;
    }

    public void setGainLoss(BigDecimal gainLoss) {
        this.gainLoss = gainLoss;
    }

    public BigDecimal getGainLossPercent() {
        return gainLossPercent;
    }

    public void setGainLossPercent(BigDecimal gainLossPercent) {
        this.gainLossPercent = gainLossPercent;
    }
}
