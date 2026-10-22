package com.lemarketjames.orders.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
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

    // Whole shares only, checked by the QUANTITY rule (see CreateOrderRequest).
    @Positive(message = "quantity must be positive")
    @Digits(integer = 10, fraction = 4)
    private BigDecimal quantity;

    /** BR-04: a dollar amount to spend on whole shares, instead of a quantity. */
    @Positive(message = "amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "amount must be dollars and cents")
    private BigDecimal amount;

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @JsonIgnore
    @AssertTrue(message = OrderSize.EXACTLY_ONE)
    public boolean isQuantityOrAmount() {
        return OrderSize.exactlyOne(quantity, amount);
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
}