package com.lemarketjames.orders.dto;

import com.lemarketjames.orders.entity.Order;
import java.math.BigDecimal;

public class CreateOrderRequest {
    private Integer accountId;
    private Integer instrumentId;
    private Order.OrderType orderType;
    private BigDecimal quantity;
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
