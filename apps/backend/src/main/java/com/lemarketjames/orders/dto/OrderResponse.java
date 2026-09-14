package com.lemarketjames.orders.dto;

import com.lemarketjames.orders.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {
    private Integer orderId;
    private Integer accountId;
    private Integer instrumentId;
    private Order.OrderType orderType;
    private BigDecimal quantity;
    private BigDecimal pricePerUnit;
    private Order.OrderStatus orderStatus;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime filledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructor from Order entity
    public OrderResponse(Order order) {
        this.orderId = order.getOrderId();
        this.accountId = order.getAccountId();
        this.instrumentId = order.getInstrumentId();
        this.orderType = order.getOrderType();
        this.quantity = order.getQuantity();
        this.pricePerUnit = order.getPricePerUnit();
        this.orderStatus = order.getOrderStatus();
        this.rejectionReason = order.getRejectionReason();
        this.submittedAt = order.getSubmittedAt();
        this.acceptedAt = order.getAcceptedAt();
        this.filledAt = order.getFilledAt();
        this.createdAt = order.getCreatedAt();
        this.updatedAt = order.getUpdatedAt();
    }
    
    // Empty constructor
    public OrderResponse() {}
    
    // Getters
    public Integer getOrderId() {
        return orderId;
    }
    
    public Integer getAccountId() {
        return accountId;
    }
    
    public Integer getInstrumentId() {
        return instrumentId;
    }
    
    public Order.OrderType getOrderType() {
        return orderType;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }
    
    public Order.OrderStatus getOrderStatus() {
        return orderStatus;
    }
    
    public String getRejectionReason() {
        return rejectionReason;
    }
    
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
    
    public LocalDateTime getFilledAt() {
        return filledAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
