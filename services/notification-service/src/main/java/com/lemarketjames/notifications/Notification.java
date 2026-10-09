package com.lemarketjames.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One status change of one order, as told to the order's client. Written once from the event and
 * never changed, so it has no setters. The table's created_at column is filled by the database
 * and is not mapped.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer notificationId;

    @Column(nullable = false)
    private Integer accountId;

    @Column(nullable = false)
    private Integer orderId;

    @Column(nullable = false, length = 20)
    private String previousStatus;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(nullable = false)
    private Instant occurredAt;

    /** For JPA. */
    protected Notification() {
    }

    /**
     * @param event   the status change being told
     * @param message what the client reads
     */
    public Notification(OrderStatusChangedMessage event, String message) {
        this.accountId = event.accountId();
        this.orderId = event.orderId();
        this.previousStatus = event.from();
        this.status = event.to();
        this.message = message;
        this.occurredAt = event.occurredAt();
    }

    public Integer getNotificationId() {
        return notificationId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
