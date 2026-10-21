package com.lemarketjames.audit;

import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retrieves archived audit events for Risk and Compliance, through Trading Ops (BR-14, contract C2).
 * Only events older than the online window come back, with no lower age bound: nothing is deleted,
 * so a seven-year-old event is found like a one-year-old one. Refused submissions, which have no
 * order and so no timeline, are found by {@code requestId} or {@code clientId}.
 */
@Service
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveRepository archive;
    private final AuditRetention retention;

    public ArchiveService(ArchiveRepository archive, AuditRetention retention) {
        this.archive = archive;
        this.retention = retention;
    }

    public List<Event> search(ArchiveQuery query) {
        return archive.search(retention.cutoff(), query).stream().map(Event::from).toList();
    }

    /** An archived event exactly as it was recorded. */
    public record Event(Long auditId, AuditEventType eventType, Integer orderId, Integer accountId,
                        Integer clientId, String requestId, Instant occurredAt, Map<String, Object> details) {
        static Event from(AuditEventEntity event) {
            return new Event(event.getAuditId(), event.getEventType(), event.getOrderId(), event.getAccountId(),
                    event.getClientId(), event.getRequestId(), event.getOccurredAt(), event.getDetails());
        }
    }
}
