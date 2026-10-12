package com.lemarketjames.surveillance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order brought to Trading Operations' attention. An alert raised when the order was placed
 * has what was ordered; one raised from a later status change has the order, its account and why.
 * Written once from the event and never changed, so it has no setters. The table's created_at
 * column is filled by the database and is not mapped.
 *
 * <p>An order has at most one alert per reason. The key is declared here as well as in
 * database/schema/019, so the tests' generated schema refuses a second one exactly as PostgreSQL does.
 */
@Entity
@Table(name = "order_alerts", uniqueConstraints = @UniqueConstraint(
    name = "order_alerts_order_reason_key", columnNames = {"order_id", "reason"}))
public class OrderAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer alertId;

    @Column(nullable = false)
    private Integer orderId;

    @Column(nullable = false)
    private Integer accountId;

    // What was ordered (instrument, side, quantity, price, submittedAt) is known only to an alert
    // raised from the placement event, so the database lets all of it be empty (019).
    private Integer instrumentId;

    @Column(length = 10)
    private String side;

    @Column(precision = 14, scale = 4)
    private BigDecimal quantity;

    /** Null for a SELL, which has no price until it fills. */
    @Column(precision = 14, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertReason reason;

    /** Why the order was rejected, for an alert about a rejection; null for every other alert. */
    @Column(length = 40)
    private String rejectionReason;

    private Instant submittedAt;

    /** When what the alert is about happened. Alerts are listed newest first by it. */
    @Column(nullable = false)
    private Instant occurredAt;

    /** For JPA. */
    protected OrderAlert() {
    }

    /**
     * An alert about an order as it was placed, so it happened when the order was submitted.
     *
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
        this.occurredAt = order.submittedAt();
    }

    /**
     * An alert about an order that was rejected, so it happened when the order changed status.
     * The status change says nothing of what was ordered, and nothing orders its topic against
     * the placement's (contract C6), so those fields stay empty; staff read them in the order's
     * timeline.
     *
     * @param rejection the order's move to REJECTED, with its reason
     * @param reason    why the alert is raised
     */
    public OrderAlert(OrderStatusChangedMessage rejection, AlertReason reason) {
        this.orderId = rejection.orderId();
        this.accountId = rejection.accountId();
        this.reason = reason;
        this.rejectionReason = rejection.rejectionReason();
        this.occurredAt = rejection.occurredAt();
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
