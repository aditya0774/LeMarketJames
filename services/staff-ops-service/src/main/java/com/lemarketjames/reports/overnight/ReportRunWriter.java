package com.lemarketjames.reports.overnight;

import com.lemarketjames.config.ReportingDataSourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The report code that writes {@code report_runs} and the run's {@code report_outputs}. Its one
 * sibling is {@link DailySummaryWriter}, which refreshes the nightly trade summary (README, rule 5).
 *
 * <p>It goes through the reports' own pool ({@link ReportingDataSourceConfig}), whose every
 * connection is read-only by default (README, "Read-side safeguards"), so a report can never write
 * by mistake. Each method here opens its own transaction and makes it read-write as its first
 * statement; PostgreSQL allows that over the default. No other report code does this.
 * (Surveillance's alerts are written through the ordinary pool, which has no such default.)
 *
 * <p>Each update names the states it expects to find, so a run only moves forward:
 * SCHEDULED → RUNNING → READY, LATE once the ready-by time passes (finished or not), and FAILED
 * from any unfinished state. A finished run never changes again.
 */
@Component
public class ReportRunWriter {

    private final JdbcTemplate jdbc;

    public ReportRunWriter(@Qualifier(ReportingDataSourceConfig.JDBC) JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates a SCHEDULED run unless the report already has one for that night.
     *
     * @return the new run's id, or empty when the report already ran (or is running) that night
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public Optional<UUID> schedule(String reportName, LocalDate runDate, Instant scheduledFor, Instant readyBy) {
        readWrite();
        // ON CONFLICT rather than a check first: the unique key decides, even with two schedulers.
        List<UUID> created = jdbc.queryForList("""
                INSERT INTO report_runs (report_name, run_date, status, scheduled_for, ready_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (report_name, run_date) DO NOTHING
                RETURNING run_id
                """, UUID.class, reportName, runDate, RunStatus.SCHEDULED.name(), utc(scheduledFor), utc(readyBy));
        return created.stream().findFirst();
    }

    /**
     * Records a night's report as FAILED without running it, because the service was down for the
     * whole of its window. Nothing changes if the report already has a run that night.
     *
     * @return whether a run was recorded
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public boolean recordMissed(String reportName, LocalDate runDate, Instant scheduledFor, Instant readyBy, String reason) {
        readWrite();
        return jdbc.update("""
                INSERT INTO report_runs (report_name, run_date, status, scheduled_for, ready_by, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (report_name, run_date) DO NOTHING
                """, reportName, runDate, RunStatus.FAILED.name(), utc(scheduledFor), utc(readyBy), reason) == 1;
    }

    /**
     * Fails every run left unfinished (waiting, running, or late and not finished). Only for
     * startup: the service just started, so nothing is running them any more.
     *
     * @return how many runs were failed
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public int failInterrupted(String reason) {
        readWrite();
        return jdbc.update("""
                UPDATE report_runs SET status = ?, failure_reason = ?
                WHERE status IN (?, ?, ?) AND ready_at IS NULL
                """, RunStatus.FAILED.name(), reason,
                RunStatus.SCHEDULED.name(), RunStatus.RUNNING.name(), RunStatus.LATE.name());
    }

    /**
     * Records that the report started. A waiting run becomes RUNNING; one the deadline already
     * made LATE stays LATE, since it still is. False when it can't start: it already started,
     * finished or failed.
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public boolean start(UUID runId, Instant now) {
        readWrite();
        return jdbc.update("""
                UPDATE report_runs
                SET status = CASE WHEN status = ? THEN ? ELSE status END, started_at = ?
                WHERE run_id = ? AND status IN (?, ?) AND started_at IS NULL
                """, RunStatus.SCHEDULED.name(), RunStatus.RUNNING.name(), utc(now),
                runId, RunStatus.SCHEDULED.name(), RunStatus.LATE.name()) == 1;
    }

    /**
     * Records that the report finished at {@code now}: READY if that is by the run's ready-by time,
     * LATE if after. The database compares, against the ready-by time stored with the run.
     * False when the run is not running.
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public boolean finish(UUID runId, Instant now) {
        readWrite();
        return jdbc.update("""
                UPDATE report_runs
                SET status = CASE WHEN ? <= ready_by THEN ? ELSE ? END, ready_at = ?
                WHERE run_id = ? AND status IN (?, ?) AND started_at IS NOT NULL AND ready_at IS NULL
                """, utc(now), RunStatus.READY.name(), RunStatus.LATE.name(), utc(now),
                runId, RunStatus.RUNNING.name(), RunStatus.LATE.name()) == 1;
    }

    /**
     * {@link #finish(UUID, Instant)}, storing the file the run produced in the same transaction:
     * only when the run did finish, so a failed or already finished run gets no file.
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public boolean finish(UUID runId, Instant now, ReportOutput output) {
        if (!finish(runId, now)) {
            return false;
        }
        jdbc.update("INSERT INTO report_outputs (run_id, file_name, content_type, body) VALUES (?, ?, ?, ?)",
                runId, output.fileName(), output.contentType(), output.body());
        return true;
    }

    /** An unfinished run to FAILED with a reason. False when it had already finished or failed. */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public boolean fail(UUID runId, String reason) {
        readWrite();
        return jdbc.update("""
                UPDATE report_runs SET status = ?, failure_reason = ?
                WHERE run_id = ? AND status IN (?, ?, ?) AND ready_at IS NULL
                """, RunStatus.FAILED.name(), reason,
                runId, RunStatus.SCHEDULED.name(), RunStatus.RUNNING.name(), RunStatus.LATE.name()) == 1;
    }

    /**
     * Marks LATE every run still waiting or running whose ready-by time has passed, so the
     * dashboard shows it is missing before it finishes.
     *
     * @return how many runs became late
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public int markLate(Instant now) {
        readWrite();
        return jdbc.update("UPDATE report_runs SET status = ? WHERE status IN (?, ?) AND ready_by <= ?",
                RunStatus.LATE.name(), RunStatus.SCHEDULED.name(), RunStatus.RUNNING.name(), utc(now));
    }

    private void readWrite() {
        // Must be the transaction's first statement; the connection's default is read-only.
        jdbc.execute("SET TRANSACTION READ WRITE");
    }

    /** The PostgreSQL driver binds OffsetDateTime to timestamptz, but not Instant. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
