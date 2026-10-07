package com.lemarketjames.reports.period;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A stretch of whole report-time-zone days, as the range of stored timestamps it covers. Orders
 * are stored in UTC without an offset, so {@code start} and {@code end} are UTC too and can be
 * compared with {@code filled_at} directly.
 *
 * <p>The range is half-open: filter with {@code filled_at >= start AND filled_at < end}, so a
 * trade on a boundary lands in exactly one period.
 *
 * @param firstDay the period's first day in the reports time zone; what a report labels the period with
 * @param start    the first instant of the period, in UTC (inclusive)
 * @param end      the first instant after the period, in UTC (exclusive)
 */
public record ReportPeriod(LocalDate firstDay, LocalDateTime start, LocalDateTime end) {

    /** Whether a stored UTC timestamp falls in this period. */
    public boolean contains(LocalDateTime utcTimestamp) {
        return !utcTimestamp.isBefore(start) && utcTimestamp.isBefore(end);
    }
}
