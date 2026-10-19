package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** LMKT-45: when the overnight reports run (AC1) and when they must be ready by (AC2), from the C5 settings. */
class OvernightScheduleConfigTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    /** US equity trading hours, the session the run must stay out of. */
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);

    private final PlatformSettings settings = new PlatformSettings();

    private OvernightScheduleConfig config() {
        return new OvernightScheduleConfig(mock(OvernightRunner.class), settings, new ReportCalendar(settings));
    }

    /** The first time a trigger fires when the clock reads {@code now}. */
    private static Instant firstAfter(Trigger trigger, Instant now) {
        return trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(now, ZoneOffset.UTC)));
    }

    /** The time a trigger fires after its last run completed at {@code completed}. */
    private static Instant afterRunCompleted(Trigger trigger, Instant completed) {
        var context = new SimpleTriggerContext(Clock.fixed(completed, ZoneOffset.UTC));
        context.update(completed, completed, completed);
        return trigger.nextExecution(context);
    }

    @Test
    void reportsRunAtTwoInTheMorningNewYorkTimeOutsideTradingHours() {
        // From mid-afternoon trading on the 14th, the next run is 02:00 EDT on the 15th.
        Instant next = firstAfter(config().runTrigger(), Instant.parse("2026-10-14T18:00:00Z"));
        assertEquals(Instant.parse("2026-10-15T06:00:00Z"), next);

        LocalTime local = next.atZone(NEW_YORK).toLocalTime();
        assertFalse(!local.isBefore(MARKET_OPEN) && local.isBefore(MARKET_CLOSE), "runs during trading hours: " + local);
    }

    @Test
    void afterARunTheNextIsTheFollowingNight() {
        assertEquals(Instant.parse("2026-10-16T06:00:00Z"),
                afterRunCompleted(config().runTrigger(), Instant.parse("2026-10-15T06:00:03Z")));
    }

    @Test
    void justBeforeTheRunTimeItRunsThatSameNight() {
        assertEquals(Instant.parse("2026-10-15T06:00:00Z"),
                firstAfter(config().runTrigger(), Instant.parse("2026-10-15T05:59:59Z")));
    }

    @Test
    void theDeadlineIsSevenInTheMorningTheSameNight() {
        assertEquals(Instant.parse("2026-10-15T11:00:00Z"),
                firstAfter(config().deadlineTrigger(), Instant.parse("2026-10-15T06:00:00Z")));
    }

    @Test
    void inWinterTheTimesAreStillNewYorkTimes() {
        assertEquals(Instant.parse("2026-12-02T07:00:00Z"),
                firstAfter(config().runTrigger(), Instant.parse("2026-12-01T18:00:00Z")));
    }

    @Test
    void theNightClocksGoForwardStillHasARun() {
        // 02:00 does not exist in New York on 2026-03-08: the run happens at 03:00 EDT instead of being skipped.
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"),
                firstAfter(config().runTrigger(), Instant.parse("2026-03-07T18:00:00Z")));
        // ...and the night after is back to 02:00.
        assertEquals(Instant.parse("2026-03-09T06:00:00Z"),
                afterRunCompleted(config().runTrigger(), Instant.parse("2026-03-08T07:00:01Z")));
    }

    @Test
    void theNightClocksGoBackRunsOnce() {
        settings.getReports().setOvernightRunTime(LocalTime.of(1, 30)); // happens twice on 2026-11-01
        Instant first = firstAfter(config().runTrigger(), Instant.parse("2026-10-31T18:00:00Z"));
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), first); // the first 01:30, EDT

        // After that run, the next is the following night, not the second 01:30 an hour later.
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), afterRunCompleted(config().runTrigger(), first.plusSeconds(5)));
    }

    @Test
    void theConfiguredTimesAreTheOnesScheduled() {
        settings.getReports().setOvernightRunTime(LocalTime.of(1, 30));
        settings.getReports().setReadyBy(LocalTime.of(6, 15));
        var registrar = new ScheduledTaskRegistrar();

        config().configureTasks(registrar);

        var triggers = registrar.getTriggerTaskList().stream().map(task -> task.getTrigger().toString()).toList();
        assertEquals(java.util.List.of("daily at 01:30 America/New_York", "daily at 06:15 America/New_York"), triggers);
    }

    @Test
    void theCatchUpRunsOnceWhenTheSchedulerStarts() {
        OvernightRunner runner = mock(OvernightRunner.class);
        var registrar = new ScheduledTaskRegistrar();
        registrar.setScheduler(new org.springframework.scheduling.concurrent.ConcurrentTaskScheduler());
        new OvernightScheduleConfig(runner, settings, new ReportCalendar(settings)).configureTasks(registrar);

        registrar.afterPropertiesSet(); // what Spring does when the scheduler starts
        try {
            verify(runner, timeout(2000).times(1)).catchUp();
            verify(runner, never()).runNight(); // the nightly triggers wait for their times
        } finally {
            registrar.destroy();
        }
    }

    @Test
    void refusesToStartWhenTheRunIsNotBeforeTheReadyByTime() {
        settings.getReports().setOvernightRunTime(LocalTime.of(7, 0));
        assertThrows(IllegalStateException.class, this::config);

        settings.getReports().setOvernightRunTime(LocalTime.of(8, 0));
        assertThrows(IllegalStateException.class, this::config);
    }
}
