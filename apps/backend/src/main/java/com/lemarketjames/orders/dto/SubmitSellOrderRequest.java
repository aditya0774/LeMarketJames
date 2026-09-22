package com.lemarketjames.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class SubmitSellOrderRequest {

    @NotNull(message = "accountId is required")
    @Positive(message = "accountId must be positive")
    private Integer accountId;

    @NotNull(message = "instrumentId is required")
    @Positive(message = "instrumentId must be positive")
    private Integer instrumentId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private BigDecimal quantity;

    public SubmitSellOrderRequest() {
    }

    public SubmitSellOrderRequest(Integer accountId, Integer instrumentId, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
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
}