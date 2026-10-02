package com.lemarketjames.orders.dto;

import com.lemarketjames.orders.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public class OrderResponse {
    private boolean success;
    private String reason;
    private String code;
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
    private String quoteSource;
    private Instant quoteTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor from Order entity (success case)
    public OrderResponse(Order order) {
        this.success = true;
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
        this.quoteSource = order.getQuoteSource();
        this.quoteTime = order.getQuoteTime();
        this.createdAt = order.getCreatedAt();
        this.updatedAt = order.getUpdatedAt();
    }
    
    // Constructor for failed validation cases
    public OrderResponse(boolean success, String reason) {
        this.success = success;
        this.reason = reason;
    }

    public OrderResponse(boolean success, String reason, String code) {
        this(success, reason);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
    
    // Empty constructor
    public OrderResponse() {
        this.success = true;
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getReason() {
        return reason;
    }
    
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

    /** The feed the execution quote came from; null until the order has been priced for execution. */
    public String getQuoteSource() {
        return quoteSource;
    }

    /** When the feed produced the execution quote (UTC); null until the order has been priced for execution. */
    public Instant getQuoteTime() {
        return quoteTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // Setters
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}
