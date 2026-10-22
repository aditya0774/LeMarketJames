package com.lemarketjames.audit;

import com.lemarketjames.common.config.PlatformSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BR-14: archival by age against the online retention window (contract C2, C5). */
class AuditRetentionTest {

    static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private static AuditRetention retention(Duration window) {
        PlatformSettings settings = new PlatformSettings();
        settings.getAudit().setOnlineRetention(window);
        return new AuditRetention(settings, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theCutoffIsOneWindowBeforeNowAndIsItselfStillOnline() {
        AuditRetention retention = retention(Duration.ofDays(365));

        assertEquals(NOW.minus(Duration.ofDays(365)), retention.cutoff());
        assertFalse(retention.isArchived(retention.cutoff()));
        assertTrue(retention.isArchived(retention.cutoff().minusMillis(1)));
        assertFalse(retention.isArchived(NOW));
    }

    @Test
    void aRecordOlderThanSevenYearsIsStillJustArchived() {
        assertTrue(retention(Duration.ofDays(365)).isArchived(NOW.minus(Duration.ofDays(8 * 365))));
    }

    @Test
    void orderTimesAreReadAsUtc() {
        AuditRetention retention = retention(Duration.ofDays(1));
        LocalDateTime cutoff = LocalDateTime.ofInstant(retention.cutoff(), ZoneOffset.UTC);

        assertFalse(retention.isArchived(cutoff));
        assertTrue(retention.isArchived(cutoff.minusSeconds(1)));
        assertFalse(retention.isArchived((LocalDateTime) null));
    }

    @Test
    void changingTheWindowReclassifiesWithoutTouchingARecord() {
        Instant twoYearsAgo = NOW.minus(Duration.ofDays(730));

        assertTrue(retention(Duration.ofDays(365)).isArchived(twoYearsAgo));
        assertFalse(retention(Duration.ofDays(3 * 365)).isArchived(twoYearsAgo));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-P1D"})
    void aWindowThatIsNotPositiveStopsTheServiceStarting(String window) {
        assertThrows(IllegalStateException.class, () -> retention(Duration.parse(window)));
    }
}
