package com.lemarketjames.orders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {
    
    public enum OrderType {
        BUY, SELL
    }
    
    public enum OrderStatus {
        SUBMITTED, ACCEPTED, PENDING, FILLED, REJECTED, DELAYED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderId;
    
    @Column(name = "account_id", nullable = false)
    private Integer accountId;
    
    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;
    
    @Column(name = "order_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private OrderType orderType;
    
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;
    
    @Column(name = "price_per_unit", precision = 14, scale = 4)
    private BigDecimal pricePerUnit;
    
    @Column(name = "order_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;
    
    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @Column(name = "filled_at")
    private LocalDateTime filledAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
        if (orderStatus == null) {
            orderStatus = OrderStatus.SUBMITTED;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public Order() {}
    
    public Order(Integer accountId, Integer instrumentId, OrderType orderType, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.orderType = orderType;
        this.quantity = quantity;
    }
    
    // Getters and Setters
    public Integer getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
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
    
    public OrderType getOrderType() {
        return orderType;
    }
    
    public void setOrderType(OrderType orderType) {
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
    
    public OrderStatus getOrderStatus() {
        return orderStatus;
    }
    
    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }
    
    public String getRejectionReason() {
        return rejectionReason;
    }
    
    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
    
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
    
    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
    
    public LocalDateTime getFilledAt() {
        return filledAt;
    }
    
    public void setFilledAt(LocalDateTime filledAt) {
        this.filledAt = filledAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", accountId=" + accountId +
                ", instrumentId=" + instrumentId +
                ", orderType=" + orderType +
                ", quantity=" + quantity +
                ", pricePerUnit=" + pricePerUnit +
                ", orderStatus=" + orderStatus +
                ", submittedAt=" + submittedAt +
                ", acceptedAt=" + acceptedAt +
                ", filledAt=" + filledAt +
                '}';
    }
}
