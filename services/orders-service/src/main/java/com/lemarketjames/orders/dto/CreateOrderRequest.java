package com.lemarketjames.orders.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lemarketjames.orders.entity.Order;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class CreateOrderRequest {
    @NotNull(message = "accountId is required")
    @Positive(message = "accountId must be positive")
    private Integer accountId;

    @NotNull(message = "instrumentId is required")
    @Positive(message = "instrumentId must be positive")
    private Integer instrumentId;

    @NotNull(message = "orderType is required")
    private Order.OrderType orderType;

    // Whole shares only, but checked by the QUANTITY rule, not here: a refused size belongs in the
    // audit trail like every other refusal (contract C2).
    @Positive(message = "quantity must be positive")
    @Digits(integer = 10, fraction = 4)
    private BigDecimal quantity;

    /** BR-04: a dollar amount to buy or sell as many whole shares of as it covers, instead of a quantity. */
    @Positive(message = "amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "amount must be dollars and cents")
    private BigDecimal amount;

    @Positive(message = "pricePerUnit must be positive")
    private BigDecimal pricePerUnit;
    
    // Constructors
    public CreateOrderRequest() {}
    
    public CreateOrderRequest(Integer accountId, Integer instrumentId, Order.OrderType orderType, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.orderType = orderType;
        this.quantity = quantity;
    }
    
    // Getters and Setters
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
    
    public Order.OrderType getOrderType() {
        return orderType;
    }
    
    public void setOrderType(Order.OrderType orderType) {
        this.orderType = orderType;
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
