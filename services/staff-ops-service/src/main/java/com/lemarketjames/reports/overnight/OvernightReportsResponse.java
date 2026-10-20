package com.lemarketjames.reports.overnight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The overnight reports list (contract C6, {@code GET /api/v1/reports/overnight}). About reports
 * only: nothing in it identifies a client.
 *
 * @param timeZone IANA ID of the reports time zone, which the staff app shows every time in
 * @param data     the most recent night first
 */
public record OvernightReportsResponse(boolean success, String timeZone, List<Run> data) {

    /**
     * One run. Instants are UTC.
     *
     * @param readyAt       when it finished: set when {@code READY}, and when {@code LATE} once the
     *                      late run has finished; otherwise null
     * @param failureReason set only when {@code FAILED}, otherwise null
     */
    public record Run(UUID id, String name, LocalDate runDate, RunStatus status, Instant scheduledFor,
                      Instant readyBy, Instant readyAt, String failureReason) {

        static Run of(ReportRun run) {
            // Enforced here as well as by the writer, so the contract holds even for a hand-edited row.
            boolean finished = run.getStatus() == RunStatus.READY || run.getStatus() == RunStatus.LATE;
            return new Run(run.getId(), run.getReportName(), run.getRunDate(), run.getStatus(),
                    run.getScheduledFor(), run.getReadyBy(),
                    finished ? run.getReadyAt() : null,
                    run.getStatus() == RunStatus.FAILED ? run.getFailureReason() : null);
        }
    }
}
