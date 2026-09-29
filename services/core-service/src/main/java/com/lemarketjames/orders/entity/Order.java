package com.lemarketjames.orders.entity;

import com.lemarketjames.orders.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "orders")
public class Order {
    
    public enum OrderType {
        BUY, SELL
    }
    
    /**
     * Where an order is in its life (contract C1). The allowed moves are defined here, in
     * {@link #allowedNext()}, and enforced by {@link Order#transitionTo}; nothing else may set a status.
     *
     * <p>Mirrors: the {@code orders.order_status} CHECK constraint (database/schema/001), the
     * frontend's {@code OrderStatus} type (apps/frontend/src/app/core/orders/order.service.ts) and
     * holdings-service's OrderSummary open-status list.
     */
    public enum OrderStatus {
        /** Saved after passing placement checks; waiting to be accepted for execution. */
        SUBMITTED,
        /** Accepted for execution; waiting for the execution engine. */
        ACCEPTED,
        /** Sent for execution; waiting for the fill. */
        PENDING,
        /** Executed; cash and holdings are settled. Final. */
        FILLED,
        /** Refused, with a {@link RejectionReason} in rejection_reason. Final. */
        REJECTED,
        /** Accepted while the market is closed; waits for the open before being sent for execution. */
        DELAYED;

        /** The statuses an order in this status may move to next; empty for final statuses. */
        public Set<OrderStatus> allowedNext() {
            return switch (this) {
                case SUBMITTED -> EnumSet.of(ACCEPTED, REJECTED);
                case ACCEPTED -> EnumSet.of(PENDING, DELAYED, FILLED, REJECTED);
                case DELAYED -> EnumSet.of(PENDING, REJECTED);
                case PENDING -> EnumSet.of(FILLED, REJECTED);
                case FILLED, REJECTED -> EnumSet.noneOf(OrderStatus.class);
            };
        }

        public boolean canMoveTo(OrderStatus next) {
            return allowedNext().contains(next);
        }

        /** Still in progress, i.e. not FILLED or REJECTED. */
        public boolean isOpen() {
            return !allowedNext().isEmpty();
        }
    }

    /**
     * Moves the order to {@code next}, stamping the matching timestamp.
     *
     * @throws InvalidStatusTransitionException if the lifecycle doesn't allow the move
     */
    public void transitionTo(OrderStatus next) {
        if (!orderStatus.canMoveTo(next)) {
            throw new InvalidStatusTransitionException(orderStatus, next);
        }
        LocalDateTime now = LocalDateTime.now();
        if (next == OrderStatus.ACCEPTED) {
            acceptedAt = now;
        } else if (next == OrderStatus.FILLED) {
            filledAt = now;
        }
        orderStatus = next;
    }

    /**
     * Rejects the order with a reason code.
     *
     * @throws InvalidStatusTransitionException if the order is already final
     */
    public void reject(RejectionReason reason) {
        transitionTo(OrderStatus.REJECTED);
        rejectionReason = reason.name();
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
        // Every new order starts its lifecycle here (contract C1).
        this.orderStatus = OrderStatus.SUBMITTED;
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
    
    /**
     * Sets the status without lifecycle checks. Only for putting an order into a known state
     * (tests, fixtures); every real status change goes through {@link #transitionTo}.
     */
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
