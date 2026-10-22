package com.lemarketjames.reports.insight;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The active-client trend: one count per report-time-zone day, oldest first. Counts only; nothing
 * here identifies a client.
 *
 * @param startDate      first day of the window (included)
 * @param endDate        last day of the window (included), today in the reports time zone
 * @param days           one entry for every day of the window, including days without trades
 * @param lastWeek       the last seven whole days, today left out
 * @param weekBefore     the seven days before {@code lastWeek}
 * @param flag           whether {@code lastWeek}'s average moved enough against {@code weekBefore}'s to be
 *                       flagged (BR-17)
 * @param generatedAt    when the figures were read
 */
public record ActiveClientTrendResponse(LocalDate startDate, LocalDate endDate, List<DayCount> days,
                                        WeekAverage lastWeek, WeekAverage weekBefore, InsightFlag flag,
                                        Instant generatedAt) {

    /** Distinct clients who traded on {@code date}. */
    public record DayCount(LocalDate date, long activeClients) {
    }

    /**
     * The average of the daily counts over seven days, both ends included, two decimals.
     */
    public record WeekAverage(LocalDate startDate, LocalDate endDate, BigDecimal averageActiveClients) {
    }
}
