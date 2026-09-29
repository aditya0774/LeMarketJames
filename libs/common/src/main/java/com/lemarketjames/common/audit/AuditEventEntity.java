package com.lemarketjames.common.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Maps to the `audit_log` table: one row per {@link AuditEventType} an order goes through. */
@Entity
@Table(name = "audit_log")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Column(name = "client_id")
    private Integer clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100)
    private AuditEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details;

    /** When the event happened, as a UTC instant. */
    @Column(name = "created_at", nullable = false)
    private Instant occurredAt;

    /** Set once the event is older than the online retention window (contract C5). */
    @Column(nullable = false)
    private boolean archived;

    protected AuditEventEntity() {
        // for JPA
    }

    public AuditEventEntity(Integer orderId, Integer accountId, Integer clientId, AuditEventType eventType,
                            Map<String, Object> details, Instant occurredAt) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.clientId = clientId;
        this.eventType = eventType;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getAuditId() {
        return auditId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Integer getClientId() {
        return clientId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public boolean isArchived() {
        return archived;
    }
}
