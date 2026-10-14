package com.lemarketjames.audit;

import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.config.PlatformSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ArchiveService {
    private final ArchiveRepository archive;
    private final PlatformSettings settings;
    private final Clock clock;

    public ArchiveService(ArchiveRepository archive, PlatformSettings settings, Clock clock) {
        this.archive = archive;
        this.settings = settings;
        this.clock = clock;
    }

    public List<Event> search(Integer orderId, String requestId, Integer clientId,
            Instant from, Instant to, int limit, int offset) {
        if ((orderId != null && orderId <= 0) || (clientId != null && clientId <= 0)
                || (requestId != null && (requestId.isBlank() || requestId.length() > 36))
                || (from != null && to != null && !from.isBefore(to))
                || limit < 1 || limit > 500 || offset < 0) {
            throw new IllegalArgumentException("Invalid archive filters, date range or pagination");
        }
        // No lower age bound: retention is indefinite, including beyond seven years from creation.
        Instant cutoff = clock.instant().minus(settings.getAudit().getOnlineRetention());
        return archive.search(cutoff, orderId, requestId, clientId, from, to, limit, offset)
                .stream().map(Event::from).toList();
    }

    public record Event(Long auditId, AuditEventType eventType, Integer orderId, Integer accountId,
            Integer clientId, String requestId, Instant occurredAt, Map<String, Object> details) {
        static Event from(AuditEventEntity event) {
            return new Event(event.getAuditId(), event.getEventType(), event.getOrderId(),
                    event.getAccountId(), event.getClientId(), event.getRequestId(),
                    event.getOccurredAt(), event.getDetails());
        }
    }
}
