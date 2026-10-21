package com.lemarketjames.audit;

import com.lemarketjames.common.config.PlatformSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Which records count as archived (BR-14, contract C2): those older than the online retention
 * window ({@link PlatformSettings.Audit#getOnlineRetention()}, contract C5).
 *
 * <p>Archival is decided by age on every request, never written to a row: audit events and final
 * orders are never updated (database/schema/016 and 029), and changing the window simply
 * reclassifies them. Nothing is ever deleted, so a record is kept for at least seven years, and in
 * fact for good.
 */
@Component
public class AuditRetention {

    private final Duration onlineRetention;
    private final Clock clock;

    public AuditRetention(PlatformSettings settings, Clock clock) {
        Duration window = settings.getAudit().getOnlineRetention();
        // A zero or negative window would archive everything, including events written a moment ago.
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("lmj.audit.online-retention must be positive, was " + window);
        }
        this.onlineRetention = window;
        this.clock = clock;
    }

    /** Records strictly older than this instant are archived; the cutoff itself is still online. */
    public Instant cutoff() {
        return clock.instant().minus(onlineRetention);
    }

    public boolean isArchived(Instant occurredAt) {
        return occurredAt != null && occurredAt.isBefore(cutoff());
    }

    /** For order timestamps, which are stored as UTC wall-clock times without a zone. */
    public boolean isArchived(LocalDateTime utc) {
        return utc != null && isArchived(utc.toInstant(ZoneOffset.UTC));
    }
}
