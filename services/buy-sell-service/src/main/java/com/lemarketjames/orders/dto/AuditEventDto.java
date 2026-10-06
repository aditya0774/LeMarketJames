package com.lemarketjames.orders.dto;

import com.lemarketjames.common.audit.AuditEventType;
import java.time.Instant;
import java.util.Map;

/**
 * A single audit event from an order's timeline.
 * 
 * <p>Represents one step in an order's lifecycle (e.g., SUBMITTED, FILLED, SETTLED).
 * Events are returned in chronological order by {@link com.lemarketjames.orders.service.OrderService#getOrderTimelineEvents(Integer)}.
 * 
 * <p>Mirrors the {@code audit_log} table; see {@link com.lemarketjames.common.audit.AuditEventEntity}.
 *
 * @param eventType the type of event (SUBMITTED, VALIDATED, ACCEPTED, FILLED, REJECTED, SETTLED)
 * @param occurredAt when the event happened, as a UTC instant
 * @param details event-specific data (keys vary by eventType; see {@link com.lemarketjames.common.audit.AuditEventType} javadoc)
 */
public record AuditEventDto(
    AuditEventType eventType,
    Instant occurredAt,
    Map<String, Object> details
) {}