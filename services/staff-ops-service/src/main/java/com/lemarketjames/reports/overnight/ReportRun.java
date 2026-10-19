package com.lemarketjames.reports.overnight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One overnight run of one report ({@code report_runs}, migration 023). Read here only: every
 * write goes through {@link ReportRunWriter}, which opens the read-write transaction the
 * otherwise read-only reports pool needs, so the entity is immutable to Hibernate.
 */
@Entity
@Immutable
@Table(name = "report_runs")
public class ReportRun {

    @Id
    @Column(name = "run_id")
    private UUID id;

    @Column(name = "report_name")
    private String reportName;

    /** The trading day the run covers, in the reports time zone. */
    @Column(name = "run_date")
    private LocalDate runDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RunStatus status;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "ready_by")
    private Instant readyBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ReportRun() {
        // For JPA.
    }

    /** For tests: a run as the database would hold it. */
    ReportRun(UUID id, String reportName, LocalDate runDate, RunStatus status, Instant scheduledFor,
              Instant readyBy, Instant readyAt, String failureReason) {
        this.id = id;
        this.reportName = reportName;
        this.runDate = runDate;
        this.status = status;
        this.scheduledFor = scheduledFor;
        this.readyBy = readyBy;
        this.readyAt = readyAt;
        this.failureReason = failureReason;
    }

    public UUID getId() {
        return id;
    }

    public String getReportName() {
        return reportName;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getReadyBy() {
        return readyBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
