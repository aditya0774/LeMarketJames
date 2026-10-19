package com.lemarketjames.reports.overnight;

/**
 * Where one overnight run of one report is (contract C6, {@code GET /api/v1/reports/overnight}).
 * This is the list C6 links to; {@code report_runs.status} stores these names as text.
 */
public enum RunStatus {
    /** Created for the night, not started yet. */
    SCHEDULED,
    /** The report job is running. */
    RUNNING,
    /** Finished by the ready-by time; {@code ready_at} says when. */
    READY,
    /**
     * Missed the ready-by time. While {@code ready_at} is empty it is still waiting or running;
     * once set, it finished, that late. A late run that then fails becomes {@link #FAILED}.
     */
    LATE,
    /** The report threw; {@code failure_reason} says why. */
    FAILED
}
