package com.lemarketjames.orders.dto;

import com.lemarketjames.orders.entity.Order;
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

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private BigDecimal quantity;

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
    
    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }
    
    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
}
