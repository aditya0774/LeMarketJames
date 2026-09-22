package com.lemarketjames.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class SubmitBuyOrderRequest {

    @NotNull(message = "accountId is required")
    @Positive(message = "accountId must be positive")
    private Integer accountId;

    @NotNull(message = "instrumentId is required")
    @Positive(message = "instrumentId must be positive")
    private Integer instrumentId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "pricePerUnit is required")
    @Positive(message = "pricePerUnit must be positive")
    private BigDecimal pricePerUnit;

    public SubmitBuyOrderRequest() {
    }

    public SubmitBuyOrderRequest(Integer accountId, Integer instrumentId, BigDecimal quantity, BigDecimal pricePerUnit) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
}