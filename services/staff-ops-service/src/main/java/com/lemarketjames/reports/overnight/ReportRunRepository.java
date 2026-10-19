package com.lemarketjames.reports.overnight;

import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads overnight runs for the Analyst dashboard's list. Extends the marker {@link Repository} so
 * no save or delete is exposed: writes go through {@link ReportRunWriter} only.
 */
public interface ReportRunRepository extends Repository<ReportRun, UUID> {

    /** Runs covering {@code firstRunDate} or later, the most recent night first. */
    List<ReportRun> findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(LocalDate firstRunDate);
}
