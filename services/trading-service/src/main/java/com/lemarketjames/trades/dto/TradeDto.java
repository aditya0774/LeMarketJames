package com.lemarketjames.trades.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A completed (FILLED) order, formatted for a trade-history view. */
public class TradeDto {

    private final String symbol;
    private final String side;
    private final BigDecimal quantity;
    private final BigDecimal pricePerUnit;
    private final LocalDateTime filledAt;

    public TradeDto(String symbol, String side, BigDecimal quantity, BigDecimal pricePerUnit, LocalDateTime filledAt) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.filledAt = filledAt;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public LocalDateTime getFilledAt() {
        return filledAt;
    }
}
