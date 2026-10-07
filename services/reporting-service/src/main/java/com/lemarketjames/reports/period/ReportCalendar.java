package com.lemarketjames.reports.period;

import com.lemarketjames.common.config.PlatformSettings;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Turns report days, weeks, months and years into ranges of stored timestamps. A report's "day"
 * is a day in the reports time zone ({@code lmj.reports.time-zone}, contract C5), while orders are
 * stored in UTC, so a trade late in the evening in New York belongs to that day even though its
 * UTC timestamp is already on the next one.
 *
 * <p>Every report endpoint takes its date ranges from here instead of converting time zones
 * itself, so all reports agree on where a day starts. The approach is the one order history uses
 * (buy-sell-service's {@code OrderHistoryPeriod}), with the zone coming from configuration
 * instead of the caller.
 */
@Component
public class ReportCalendar {

    private final PlatformSettings settings;

    public ReportCalendar(PlatformSettings settings) {
        this.settings = settings;
    }

    /** The time zone report days are in. */
    public ZoneId zone() {
        return settings.getReports().getTimeZone();
    }

    /**
     * The range a report asks for: every day from {@code firstDay} to {@code lastDay}, both included.
     *
     * @throws IllegalArgumentException when a day is missing or the range is reversed; the shared
     *         exception handler answers that with 400
     */
    public ReportPeriod between(LocalDate firstDay, LocalDate lastDay) {
        if (firstDay == null || lastDay == null) {
            throw new IllegalArgumentException("Both the first and the last day of the report are required");
        }
        if (lastDay.isBefore(firstDay)) {
            throw new IllegalArgumentException("The report's last day must not be before its first day");
        }
        return period(firstDay, lastDay.plusDays(1));
    }

    /** The whole day, week, month or year that a report-time-zone day falls in. */
    public ReportPeriod bucketOf(LocalDate day, ReportBucket bucket) {
        LocalDate firstDay = bucket.firstDay(day);
        return period(firstDay, bucket.firstDayOfNext(firstDay));
    }

    /** The whole day, week, month or year that a stored UTC timestamp (e.g. {@code filled_at}) falls in. */
    public ReportPeriod bucketContaining(LocalDateTime utcTimestamp, ReportBucket bucket) {
        return bucketOf(reportDay(utcTimestamp), bucket);
    }

    /** The day in the reports time zone on which a stored UTC timestamp happened. */
    public LocalDate reportDay(LocalDateTime utcTimestamp) {
        return utcTimestamp.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone()).toLocalDate();
    }

    private ReportPeriod period(LocalDate firstDay, LocalDate dayAfterLast) {
        // Resolve both local midnights independently: DST days need not contain 24 hours.
        return new ReportPeriod(firstDay, startInUtc(firstDay), startInUtc(dayAfterLast));
    }

    private LocalDateTime startInUtc(LocalDate day) {
        return LocalDateTime.ofInstant(day.atStartOfDay(zone()).toInstant(), ZoneOffset.UTC);
    }
}
