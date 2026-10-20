package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LMKT-45: what one night of overnight reports does, with the default settings (run at 02:00,
 * ready by 07:00, New York) and the writer mocked. The schedule itself is OvernightScheduleConfigTest's.
 */
class OvernightRunnerTest {

    /** 02:00 EDT on the 15th: the night's run time. */
    private static final Instant RUN_TIME = Instant.parse("2026-10-15T06:00:00Z");
    /** 07:00 EDT on the 15th. */
    private static final Instant READY_BY = Instant.parse("2026-10-15T11:00:00Z");
    /** The trading day the night's runs cover. */
    private static final LocalDate RUN_DATE = LocalDate.parse("2026-10-14");

    private final MovableClock clock = new MovableClock(RUN_TIME);
    private final ReportRunWriter writer = mock(ReportRunWriter.class);
    private final PlatformSettings settings = new PlatformSettings();

    @BeforeEach
    void eachRunCanStart() {
        when(writer.start(any(), any())).thenReturn(true);
    }

    private OvernightRunner runner(OvernightReportJob... jobs) {
        return new OvernightRunner(List.of(jobs), writer, new ReportCalendar(settings), settings, clock);
    }

    /** Schedules the job's run and returns its id, as the writer would for a new night. */
    private UUID scheduled(OvernightReportJob job) {
        UUID id = UUID.randomUUID();
        when(writer.schedule(job.name(), RUN_DATE, RUN_TIME, READY_BY)).thenReturn(Optional.of(id));
        return id;
    }

    @Test
    void runsEveryOvernightReportOnTheTradingDayBefore() {
        Job stocks = new Job("Activity by stock", day -> { });
        Job segments = new Job("Activity by client segment", day -> { });
        scheduled(stocks);
        scheduled(segments);

        runner(stocks, segments).runNight();

        assertEquals(List.of(RUN_DATE), stocks.ranOn);
        assertEquals(List.of(RUN_DATE), segments.ranOn);
    }

    @Test
    void aSuccessfulRunIsReadyWithTheTimeItFinished() {
        Job job = new Job("Activity by stock", day -> clock.advance(Duration.ofMinutes(4)));
        UUID id = scheduled(job);

        runner(job).runNight();

        verify(writer).start(id, RUN_TIME);
        verify(writer).finish(id, RUN_TIME.plus(Duration.ofMinutes(4)));
        verify(writer, never()).fail(any(), anyString());
    }

    @Test
    void aFailedRunIsRecordedWithItsReason() {
        Job job = new Job("Activity by stock", day -> { throw new IllegalStateException("Source data missing for 2026-10-14"); });
        UUID id = scheduled(job);

        runner(job).runNight();

        verify(writer).fail(id, "Source data missing for 2026-10-14");
        verify(writer, never()).finish(any(), any());
    }

    @Test
    void oneFailedReportDoesNotStopTheNext() {
        Job broken = new Job("Broken", day -> { throw new IllegalStateException("boom"); });
        Job fine = new Job("Fine", day -> { });
        UUID brokenId = scheduled(broken);
        UUID fineId = scheduled(fine);

        runner(broken, fine).runNight();

        verify(writer).fail(brokenId, "boom");
        verify(writer).finish(fineId, RUN_TIME);
    }

    @Test
    void aReportThatAlreadyHasARunTonightIsNotRunAgain() {
        Job job = new Job("Activity by stock", day -> { });
        when(writer.schedule(job.name(), RUN_DATE, RUN_TIME, READY_BY)).thenReturn(Optional.empty());

        runner(job).runNight();

        assertEquals(List.of(), job.ranOn);
        verify(writer, never()).start(any(), any());
    }

    @Test
    void aRunThatCannotStartIsNotRun() {
        Job job = new Job("Activity by stock", day -> { });
        UUID id = scheduled(job);
        when(writer.start(id, RUN_TIME)).thenReturn(false);

        runner(job).runNight();

        assertEquals(List.of(), job.ranOn);
        verify(writer, never()).finish(any(), any());
    }

    @Test
    void aRunFinishingAfterTheReadyByTimeIsRecordedWithItsRealFinishTime() {
        // The writer makes it LATE, comparing with the stored ready-by time (OvernightRunsIntegrationTest).
        Job job = new Job("Activity by stock", day -> clock.set(READY_BY.plusSeconds(1)));
        UUID id = scheduled(job);

        runner(job).runNight();

        verify(writer).finish(id, READY_BY.plusSeconds(1));
        verify(writer, never()).fail(any(), anyString());
    }

    @Test
    void theDeadlineMarksEveryUnfinishedRunLate() {
        clock.set(READY_BY);

        runner().enforceDeadline();

        verify(writer).markLate(READY_BY);
    }

    @Test
    void aRestartBeforeTheReadyByTimeRunsTheMissingReportsNow() {
        clock.set(Instant.parse("2026-10-15T07:00:00Z")); // 03:00 EDT: after the run time, before 07:00
        Job job = new Job("Activity by stock", day -> { });
        UUID id = scheduled(job);

        runner(job).catchUp();

        assertEquals(List.of(RUN_DATE), job.ranOn);
        verify(writer).finish(id, clock.instant());
        verify(writer, never()).recordMissed(any(), any(), any(), any(), any());
    }

    @Test
    void aStartAfterTheReadyByTimeRecordsTheNightAsMissedWithoutRunningInTradingHours() {
        clock.set(Instant.parse("2026-10-15T19:00:00Z")); // 15:00 EDT, the market is open
        Job job = new Job("Activity by stock", day -> { });

        runner(job).catchUp();

        assertEquals(List.of(), job.ranOn);
        verify(writer).recordMissed("Activity by stock", RUN_DATE, RUN_TIME, READY_BY, OvernightRunner.MISSED_REASON);
        verify(writer, never()).schedule(any(), any(), any(), any());
    }

    @Test
    void aStartBeforeTonightsRunTimeChecksLastNight() {
        clock.set(Instant.parse("2026-10-15T05:00:00Z")); // 01:00 EDT on the 15th: tonight's 02:00 is still to come
        Job job = new Job("Activity by stock", day -> { });

        runner(job).catchUp();

        // Last night is the 14th's: its 02:00 run covered the 13th, and its 07:00 has long passed.
        verify(writer).recordMissed("Activity by stock", LocalDate.parse("2026-10-13"),
                Instant.parse("2026-10-14T06:00:00Z"), Instant.parse("2026-10-14T11:00:00Z"), OvernightRunner.MISSED_REASON);
        assertEquals(List.of(), job.ranOn);
    }

    @Test
    void catchingUpFirstFailsRunsTheLastProcessLeftUnfinished() {
        clock.set(Instant.parse("2026-10-15T07:00:00Z"));
        Job job = new Job("Activity by stock", day -> { });
        scheduled(job);

        runner(job).catchUp();

        var order = org.mockito.Mockito.inOrder(writer);
        order.verify(writer).failInterrupted(OvernightRunner.INTERRUPTED_REASON);
        order.verify(writer).schedule(any(), any(), any(), any());
    }

    @Test
    void theNightFollowsTheConfiguredTimesAndZone() {
        settings.getReports().setOvernightRunTime(java.time.LocalTime.of(1, 30));
        settings.getReports().setReadyBy(java.time.LocalTime.of(6, 0));
        settings.getReports().setTimeZone(ZoneId.of("Europe/London"));
        clock.set(Instant.parse("2026-12-02T01:30:00Z")); // 01:30 GMT
        Job job = new Job("Activity by stock", day -> { });

        runner(job).runNight();

        verify(writer).schedule("Activity by stock", LocalDate.parse("2026-12-01"),
                Instant.parse("2026-12-02T01:30:00Z"), Instant.parse("2026-12-02T06:00:00Z"));
    }

    @Test
    void aFailureReasonIsReadable() {
        assertEquals("IllegalStateException", OvernightRunner.reasonFor(new IllegalStateException()));
        assertEquals("IllegalStateException", OvernightRunner.reasonFor(new IllegalStateException("  ")));
        assertEquals("Timed out", OvernightRunner.reasonFor(new RuntimeException("  Timed out \n")));
        String tooLong = "x".repeat(OvernightRunner.MAX_REASON_LENGTH + 20);
        assertEquals(OvernightRunner.MAX_REASON_LENGTH, OvernightRunner.reasonFor(new RuntimeException(tooLong)).length());
    }

    @Test
    void schedulesWithTheNightsRunAndReadyByTimes() {
        Job job = new Job("Activity by stock", day -> { });

        runner(job).runNight();

        verify(writer).schedule(eq("Activity by stock"), eq(RUN_DATE), eq(RUN_TIME), eq(READY_BY));
    }

    /** A report job that records the days it ran on and does what the test says. */
    private static final class Job implements OvernightReportJob {
        private final String name;
        private final Consumer<LocalDate> work;
        final List<LocalDate> ranOn = new java.util.ArrayList<>();

        Job(String name, Consumer<LocalDate> work) {
            this.name = name;
            this.work = work;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void run(LocalDate runDate) {
            ranOn.add(runDate);
            work.accept(runDate);
        }
    }

    /** A clock a test can move, so a job can "take" time. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
