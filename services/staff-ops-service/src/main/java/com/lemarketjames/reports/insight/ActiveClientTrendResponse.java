package com.lemarketjames.reports.insight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The active-client trend: one count per report-time-zone day, oldest first. Counts only; nothing
 * here identifies a client.
 *
 * @param startDate   first day of the window (included)
 * @param endDate     last day of the window (included), today in the reports time zone
 * @param days        one entry for every day of the window, including days without trades
 * @param generatedAt when the figures were read
 */
public record ActiveClientTrendResponse(LocalDate startDate, LocalDate endDate, List<DayCount> days,
                                        Instant generatedAt) {

    /** Distinct clients who traded on {@code date}. */
    public record DayCount(LocalDate date, long activeClients) {
    }
}
