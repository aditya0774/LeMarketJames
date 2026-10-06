package com.lemarketjames.common.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Maps to the `audit_log` table: one row per {@link AuditEventType} an order goes through.
 *
 * <p>Immutable: once stored, an event is never updated (contract C2), so Hibernate never writes a
 * change to a loaded event back. There are no setters either.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    /** Null for a refused submission: no order was saved, so {@link #requestId} identifies it. */
    @Column(name = "order_id")
    private Integer orderId;

    /** Null when the caller was refused access to the account they asked for. */
    @Column(name = "account_id")
    private Integer accountId;

    /** The submission request that caused the event; set on submission events only. */
    @Column(name = "request_id", length = 36)
    private String requestId;

    /**
     * Unique per submission, event type and rule, so the same event can't be stored twice. Null for
     * events that aren't part of a submission. Declared unique here so the H2 test schema enforces
     * it like the index in database/schema/014.
     */
    @Column(name = "event_key", length = 120, unique = true)
    private String eventKey;

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

    /** A submission event, which also carries the request it belongs to and its unique key. */
    public AuditEventEntity(SubmissionAuditEvent event, Instant occurredAt) {
        this(event.orderId(), event.accountId(), event.clientId(), event.type(), event.details(), occurredAt);
        this.requestId = event.requestId();
        this.eventKey = event.eventKey();
    }

    public Long getAuditId() {
        return auditId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getEventKey() {
        return eventKey;
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
