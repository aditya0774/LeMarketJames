package com.lemarketjames.surveillance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order brought to Trading Operations' attention, with what was ordered when it was placed.
 * Written once from the event and never changed, so it has no setters. The table's created_at
 * column is filled by the database and is not mapped.
 */
@Entity
@Table(name = "order_alerts")
public class OrderAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer alertId;

    @Column(nullable = false, unique = true)
    private Integer orderId;

    @Column(nullable = false)
    private Integer accountId;

    @Column(nullable = false)
    private Integer instrumentId;

    @Column(nullable = false, length = 10)
    private String side;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    /** Null for a SELL, which has no price until it fills. */
    @Column(precision = 14, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertReason reason;

    @Column(nullable = false)
    private Instant submittedAt;

    /** For JPA. */
    protected OrderAlert() {
    }

    /**
     * @param order  the order the alert is about
     * @param reason why it is raised
     */
    public OrderAlert(OrderSubmittedMessage order, AlertReason reason) {
        this.orderId = order.orderId();
        this.accountId = order.accountId();
        this.instrumentId = order.instrumentId();
        this.side = order.side();
        this.quantity = order.quantity();
        this.price = order.price();
        this.reason = reason;
        this.submittedAt = order.submittedAt();
    }

    public Integer getAlertId() {
        return alertId;
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

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public AlertReason getReason() {
        return reason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
