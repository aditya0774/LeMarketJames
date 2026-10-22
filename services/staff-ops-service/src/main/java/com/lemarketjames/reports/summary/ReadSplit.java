package com.lemarketjames.reports.summary;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Which days of a report's range come from the nightly summary and which from the live view
 * (BR-16, README "Read side"). The summary covers every day up to its {@code summarizedThrough}
 * day; the rest of the range (today, and any night not summarized yet) is read live.
 *
 * @param summarized the days read from {@code reporting_daily_trades}, if any
 * @param live       the days read from {@code reporting_trades}, if any
 */
public record ReadSplit(Optional<DayRange> summarized, Optional<DayRange> live) {

    /** Days {@code from} to {@code to}, both included, in the reports time zone. */
    public record DayRange(LocalDate from, LocalDate to) {
    }

    /**
     * @param from               first day of the report, included
     * @param to                 last day of the report, included
     * @param summarizedThrough the last day the summary covers, or empty when there is no summary yet
     */
    public static ReadSplit of(LocalDate from, LocalDate to, Optional<LocalDate> summarizedThrough) {
        if (summarizedThrough.isEmpty() || summarizedThrough.get().isBefore(from)) {
            return new ReadSplit(Optional.empty(), Optional.of(new DayRange(from, to)));
        }
        LocalDate through = summarizedThrough.get();
        if (!through.isBefore(to)) {
            return new ReadSplit(Optional.of(new DayRange(from, to)), Optional.empty());
        }
        return new ReadSplit(Optional.of(new DayRange(from, through)), Optional.of(new DayRange(through.plusDays(1), to)));
    }
}
