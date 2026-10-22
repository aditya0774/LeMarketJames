package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs each night's overnight reports and records how each run went (LMKT-45). The schedule
 * itself is {@link OvernightScheduleConfig}'s; this class only knows what a night is, so it can be
 * tested with a fixed clock.
 *
 * <p>A night is the reports-time-zone day the run time falls on, and its runs cover the trading
 * day before it: the 02:00 run on the 15th reports on the 14th. Jobs run one after another, never
 * in parallel, because the service's connection pool is deliberately small.
 */
@Component
public class OvernightRunner {

    /** The {@code report_runs.failure_reason} column's length. */
    static final int MAX_REASON_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(OvernightRunner.class);

    private final List<OvernightReportJob> jobs;
    private final ReportRunWriter writer;
    private final ReportCalendar calendar;
    private final PlatformSettings settings;
    private final Clock clock;

    public OvernightRunner(List<OvernightReportJob> jobs, ReportRunWriter writer, ReportCalendar calendar,
                           PlatformSettings settings, Clock clock) {
        this.jobs = jobs;
        this.writer = writer;
        this.calendar = calendar;
        this.settings = settings;
        this.clock = clock;
    }

    /** The reason a night's report shows when the service was down for its whole window. */
    static final String MISSED_REASON = "Did not run: staff-ops-service was not running at the overnight run time";
    /** The reason a run shows when the service stopped while it was waiting or running. */
    static final String INTERRUPTED_REASON = "Interrupted: staff-ops-service stopped before the run finished";

    /**
     * Schedules tonight's run of every overnight report, then runs them. A report that already has
     * a run tonight (the service restarted, or the trigger fired twice) is left alone.
     */
    public void runNight() {
        runNightOf(calendar.reportDay(clock.instant()));
    }

    /**
     * At startup: makes sure the most recent night is in the list even if the service was not
     * running at its run time, so the list never just leaves a night out.
     *
     * <ol>
     *   <li>Runs left unfinished are failed as interrupted: the service just started, so whatever
     *       was running them is gone, and they would otherwise wait forever.</li>
     *   <li>If the most recent run time has passed but its ready-by time has not (a restart at
     *       03:00), the missing reports run now.</li>
     *   <li>If its ready-by time has passed too (a start at 15:00), the missing reports are not
     *       run, which would be during trading hours (AC1); each is recorded as failed instead.</li>
     * </ol>
     * Only the most recent night is checked: a service down for days records only the last one.
     */
    public void catchUp() {
        int interrupted = writer.failInterrupted(INTERRUPTED_REASON);
        if (interrupted > 0) {
            log.warn("{} overnight report run(s) were interrupted by a restart", interrupted);
        }

        Instant now = clock.instant();
        LocalDate night = calendar.reportDay(now);
        if (now.isBefore(calendar.at(night, settings.getReports().getOvernightRunTime()))) {
            night = night.minusDays(1); // today's run time hasn't come yet: the last night is yesterday's
        }
        if (now.isBefore(calendar.at(night, settings.getReports().getReadyBy()))) {
            runNightOf(night);
            return;
        }
        LocalDate runDate = night.minusDays(1);
        Instant scheduledFor = calendar.at(night, settings.getReports().getOvernightRunTime());
        Instant readyBy = calendar.at(night, settings.getReports().getReadyBy());
        for (OvernightReportJob job : jobs) {
            if (writer.recordMissed(job.name(), runDate, scheduledFor, readyBy, MISSED_REASON)) {
                log.warn("Overnight report '{}' for {} did not run: the service was down at its run time", job.name(), runDate);
            }
        }
    }

    private void runNightOf(LocalDate night) {
        LocalDate runDate = night.minusDays(1);
        Instant scheduledFor = calendar.at(night, settings.getReports().getOvernightRunTime());
        Instant readyBy = calendar.at(night, settings.getReports().getReadyBy());

        // Scheduled first, run second: the list shows every report of the night as soon as it starts.
        List<ScheduledRun> tonight = new ArrayList<>();
        for (OvernightReportJob job : jobs) {
            Optional<UUID> run = writer.schedule(job.name(), runDate, scheduledFor, readyBy);
            if (run.isPresent()) {
                tonight.add(new ScheduledRun(job, run.get()));
            } else {
                log.info("Overnight report '{}' already has a run for {}; not running it again", job.name(), runDate);
            }
        }
        tonight.forEach(run -> runOne(run.job(), run.runId(), runDate));
    }

    private record ScheduledRun(OvernightReportJob job, UUID runId) {
    }

    /**
     * At the ready-by time: marks LATE every run not finished yet, so the dashboard shows it is
     * missing while it still waits or runs (AC2). Those runs carry on; when one finishes it stays LATE.
     */
    public void enforceDeadline() {
        int late = writer.markLate(clock.instant());
        if (late > 0) {
            log.warn("{} overnight report run(s) were not ready by the ready-by time", late);
        }
    }

    private void runOne(OvernightReportJob job, UUID runId, LocalDate runDate) {
        if (!writer.start(runId, clock.instant())) {
            return; // already started, finished or failed: nothing left to run
        }
        Optional<ReportOutput> output;
        try {
            output = job.run(runDate);
        } catch (RuntimeException failure) {
            // The analyst sees the message; the stack trace is for the log only.
            log.error("Overnight report '{}' for {} failed", job.name(), runDate, failure);
            writer.fail(runId, reasonFor(failure));
            return;
        }
        // READY, or LATE if this is after the run's ready-by time: the writer decides, against the stored time.
        // A file is stored in the same transaction, so a finished run and its output never disagree.
        if (output.isPresent()) {
            writer.finish(runId, clock.instant(), output.get());
        } else {
            writer.finish(runId, clock.instant());
        }
    }

    /** A reason an analyst can read: the exception's message, or its kind when it has none. */
    static String reasonFor(RuntimeException failure) {
        String message = failure.getMessage();
        String reason = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message.strip();
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
