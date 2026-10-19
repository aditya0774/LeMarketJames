package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LMKT-45: which runs the overnight reports list holds, and what each says. */
class OvernightReportsServiceTest {

    private static final Instant SCHEDULED_FOR = Instant.parse("2026-10-15T06:00:00Z");
    private static final Instant READY_BY = Instant.parse("2026-10-15T11:00:00Z");

    private final ReportRunRepository runs = mock(ReportRunRepository.class);
    // 21:00 EDT on the 14th, already the 15th in UTC: "today" must be the reports zone's day.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"), ZoneOffset.UTC);
    private final OvernightReportsService service =
            new OvernightReportsService(runs, new ReportCalendar(new PlatformSettings()), clock);

    private static ReportRun run(RunStatus status, Instant readyAt, String failureReason) {
        return new ReportRun(UUID.randomUUID(), "Activity by stock", LocalDate.parse("2026-10-14"), status,
                SCHEDULED_FOR, READY_BY, readyAt, failureReason);
    }

    @Test
    void listsTheLastThirtyNightsInTheReportsTimeZone() {
        service.recentRuns();

        verify(runs).findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(LocalDate.parse("2026-09-14"));
    }

    @Test
    void saysWhichTimeZoneTheTimesAreFor() {
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any())).thenReturn(List.of());

        var response = service.recentRuns();

        assertTrue(response.success());
        assertEquals("America/New_York", response.timeZone());
        assertEquals(List.of(), response.data());
    }

    @Test
    void aReadyRunHasItsReadyTimeAndNoReason() {
        Instant readyAt = Instant.parse("2026-10-15T06:04:12Z");
        ReportRun ready = run(RunStatus.READY, readyAt, null);
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any())).thenReturn(List.of(ready));

        var item = service.recentRuns().data().get(0);

        assertEquals(ready.getId(), item.id());
        assertEquals("Activity by stock", item.name());
        assertEquals(LocalDate.parse("2026-10-14"), item.runDate());
        assertEquals(RunStatus.READY, item.status());
        assertEquals(SCHEDULED_FOR, item.scheduledFor());
        assertEquals(READY_BY, item.readyBy());
        assertEquals(readyAt, item.readyAt());
        assertNull(item.failureReason());
    }

    @Test
    void aFailedRunHasItsReasonAndNoReadyTime() {
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any()))
                .thenReturn(List.of(run(RunStatus.FAILED, null, "Source data missing")));

        var item = service.recentRuns().data().get(0);

        assertEquals(RunStatus.FAILED, item.status());
        assertEquals("Source data missing", item.failureReason());
        assertNull(item.readyAt());
    }

    @Test
    void aLateRunThatFinishedHasItsLateReadyTime() {
        Instant finishedLate = Instant.parse("2026-10-15T11:12:00Z"); // 07:12 EDT, after 07:00
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any()))
                .thenReturn(List.of(run(RunStatus.LATE, finishedLate, null)));

        var item = service.recentRuns().data().get(0);

        assertEquals(RunStatus.LATE, item.status());
        assertEquals(finishedLate, item.readyAt());
        assertNull(item.failureReason());
    }

    @Test
    void aLateRunStillGoingHasNoReadyTime() {
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any()))
                .thenReturn(List.of(run(RunStatus.LATE, null, null)));

        var item = service.recentRuns().data().get(0);

        assertEquals(RunStatus.LATE, item.status());
        assertNull(item.readyAt());
    }

    @Test
    void anUnfinishedRunShowsNeitherAReadyTimeNorAReason() {
        // Even if a row somehow holds both, the contract says they belong to READY and FAILED only.
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any()))
                .thenReturn(List.of(run(RunStatus.RUNNING, Instant.parse("2026-10-15T06:01:00Z"), "stale")));

        var item = service.recentRuns().data().get(0);

        assertNull(item.readyAt());
        assertNull(item.failureReason());
    }

    @Test
    void keepsTheRepositorysOrder() {
        ReportRun newer = new ReportRun(UUID.randomUUID(), "A", LocalDate.parse("2026-10-14"), RunStatus.READY,
                SCHEDULED_FOR, READY_BY, SCHEDULED_FOR, null);
        ReportRun older = new ReportRun(UUID.randomUUID(), "A", LocalDate.parse("2026-10-13"), RunStatus.READY,
                SCHEDULED_FOR, READY_BY, SCHEDULED_FOR, null);
        when(runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(any())).thenReturn(List.of(newer, older));

        var dates = service.recentRuns().data().stream().map(OvernightReportsResponse.Run::runDate).toList();

        assertEquals(List.of(LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-13")), dates);
    }
}
