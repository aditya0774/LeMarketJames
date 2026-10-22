package com.lemarketjames.reports.overnight;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A report heavy enough to run overnight. Being a Spring bean of this type is what marks a
 * report as overnight: {@link OvernightRunner} runs every one of them each night, so a real
 * report joins by adding a bean and nothing else changes.
 */
public interface OvernightReportJob {

    /** The name analysts see in the overnight reports list. Also the run's key: one run per name per night. */
    String name();

    /**
     * Produces the report for one trading day. Returning means it succeeded; any exception means it
     * failed, and its message becomes the reason the analyst sees, so make it readable.
     *
     * @param runDate the trading day to report on, in the reports time zone
     * @return the file the analyst downloads, stored with the run when it finishes; empty when the
     *         report produces none
     */
    Optional<ReportOutput> run(LocalDate runDate);
}
