package com.lemarketjames.reports.period;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** The calendar periods a report can group trades into. Turn one into a time range with {@link ReportCalendar}. */
public enum ReportBucket {
    DAY {
        @Override
        LocalDate firstDay(LocalDate day) {
            return day;
        }

        @Override
        LocalDate firstDayOfNext(LocalDate firstDay) {
            return firstDay.plusDays(1);
        }
    },
    /**
     * Monday to Sunday (ISO-8601), the same weeks PostgreSQL's {@code date_trunc('week', ...)}
     * makes, so a report grouped in SQL and one grouped in Java agree.
     */
    WEEK {
        @Override
        LocalDate firstDay(LocalDate day) {
            return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        @Override
        LocalDate firstDayOfNext(LocalDate firstDay) {
            return firstDay.plusWeeks(1);
        }
    },
    MONTH {
        @Override
        LocalDate firstDay(LocalDate day) {
            return day.withDayOfMonth(1);
        }

        @Override
        LocalDate firstDayOfNext(LocalDate firstDay) {
            return firstDay.plusMonths(1);
        }
    },
    YEAR {
        @Override
        LocalDate firstDay(LocalDate day) {
            return day.withDayOfYear(1);
        }

        @Override
        LocalDate firstDayOfNext(LocalDate firstDay) {
            return firstDay.plusYears(1);
        }
    };

    /** The first day of the bucket that {@code day} falls in. */
    abstract LocalDate firstDay(LocalDate day);

    /** The first day of the bucket after the one starting on {@code firstDay}. */
    abstract LocalDate firstDayOfNext(LocalDate firstDay);
}
