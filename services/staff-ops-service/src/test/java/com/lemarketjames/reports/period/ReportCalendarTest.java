package com.lemarketjames.reports.period;

import com.lemarketjames.common.config.PlatformSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCalendarTest {

    /** New York is UTC-5 in winter and UTC-4 in summer, so both offsets and both DST changes are covered. */
    private final ReportCalendar newYork = calendarIn("America/New_York");

    private static ReportCalendar calendarIn(String zone) {
        PlatformSettings settings = new PlatformSettings();
        settings.getReports().setTimeZone(ZoneId.of(zone));
        return new ReportCalendar(settings);
    }

    @Test
    void usesTheConfiguredReportsTimeZone() {
        PlatformSettings settings = new PlatformSettings();
        assertEquals(settings.getReports().getTimeZone(), new ReportCalendar(settings).zone());

        settings.getReports().setTimeZone(ZoneId.of("Asia/Kolkata"));
        assertEquals(ZoneId.of("Asia/Kolkata"), new ReportCalendar(settings).zone());
    }

    @ParameterizedTest
    @CsvSource({
        // An ordinary winter day starts at 05:00 UTC.
        "DAY,2026-01-15,2026-01-15,2026-01-15T05:00,2026-01-16T05:00",
        // Clocks go forward: the day has 23 hours.
        "DAY,2026-03-08,2026-03-08,2026-03-08T05:00,2026-03-09T04:00",
        // Clocks go back: the day has 25 hours.
        "DAY,2026-11-01,2026-11-01,2026-11-01T04:00,2026-11-02T05:00",
        // A Wednesday belongs to the week that started on Monday.
        "WEEK,2026-01-14,2026-01-12,2026-01-12T05:00,2026-01-19T05:00",
        // A Sunday is the last day of its week, and a week can start in the previous year.
        "WEEK,2026-01-04,2025-12-29,2025-12-29T05:00,2026-01-05T05:00",
        // March starts in winter time and ends in summer time.
        "MONTH,2026-03-20,2026-03-01,2026-03-01T05:00,2026-04-01T04:00",
        "MONTH,2024-02-29,2024-02-01,2024-02-01T05:00,2024-03-01T05:00",
        "YEAR,2026-07-04,2026-01-01,2026-01-01T05:00,2027-01-01T05:00"
    })
    void bucketCoversItsWholeCalendarPeriodInUtc(ReportBucket bucket, LocalDate day, LocalDate firstDay,
                                                LocalDateTime start, LocalDateTime end) {
        assertEquals(new ReportPeriod(firstDay, start, end), newYork.bucketOf(day, bucket));
    }

    @Test
    void aTradeJustBeforeLocalMidnightBelongsToThePreviousDay() {
        // 23:59:59 on the 14th in New York is already the 15th in UTC.
        LocalDateTime lastMoment = LocalDateTime.parse("2026-01-15T04:59:59.999999");
        LocalDateTime midnight = LocalDateTime.parse("2026-01-15T05:00:00");

        ReportPeriod fourteenth = newYork.bucketContaining(lastMoment, ReportBucket.DAY);
        ReportPeriod fifteenth = newYork.bucketContaining(midnight, ReportBucket.DAY);

        assertEquals(LocalDate.parse("2026-01-14"), fourteenth.firstDay());
        assertEquals(LocalDate.parse("2026-01-15"), fifteenth.firstDay());
        // Neighbouring days share the boundary instant, and only the later day contains it.
        assertEquals(fourteenth.end(), fifteenth.start());
        assertTrue(fourteenth.contains(lastMoment));
        assertFalse(fourteenth.contains(midnight));
        assertTrue(fifteenth.contains(midnight));
    }

    @Test
    void aTradeInTheFirstUtcHoursOfTheYearBelongsToThePreviousMonthAndYear() {
        LocalDateTime newYearsEveInNewYork = LocalDateTime.parse("2026-01-01T03:00:00");

        assertEquals(LocalDate.parse("2025-12-31"), newYork.reportDay(newYearsEveInNewYork));
        assertEquals(LocalDate.parse("2025-12-01"),
                newYork.bucketContaining(newYearsEveInNewYork, ReportBucket.MONTH).firstDay());
        assertEquals(LocalDate.parse("2025-01-01"),
                newYork.bucketContaining(newYearsEveInNewYork, ReportBucket.YEAR).firstDay());
    }

    @Test
    void theSameInstantFallsOnDifferentDaysInDifferentReportZones() {
        LocalDateTime stored = LocalDateTime.parse("2026-01-15T20:00:00");

        assertEquals(LocalDate.parse("2026-01-15"), newYork.reportDay(stored));
        assertEquals(LocalDate.parse("2026-01-15"), calendarIn("UTC").reportDay(stored));
        // 01:30 the next morning in India.
        assertEquals(LocalDate.parse("2026-01-16"), calendarIn("Asia/Kolkata").reportDay(stored));
        assertEquals(new ReportPeriod(LocalDate.parse("2026-01-16"),
                        LocalDateTime.parse("2026-01-15T18:30"), LocalDateTime.parse("2026-01-16T18:30")),
                calendarIn("Asia/Kolkata").bucketContaining(stored, ReportBucket.DAY));
    }

    @Test
    void requestedRangeIncludesBothItsFirstAndLastDay() {
        ReportPeriod january = newYork.between(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));

        assertEquals(new ReportPeriod(LocalDate.parse("2026-01-01"),
                LocalDateTime.parse("2026-01-01T05:00"), LocalDateTime.parse("2026-02-01T05:00")), january);
        // The last day's final moment is in; the next day's first moment is out.
        assertTrue(january.contains(LocalDateTime.parse("2026-02-01T04:59:59")));
        assertFalse(january.contains(LocalDateTime.parse("2026-02-01T05:00:00")));
        assertFalse(january.contains(LocalDateTime.parse("2026-01-01T04:59:59")));
    }

    @Test
    void aSingleDayRangeIsThatDay() {
        LocalDate day = LocalDate.parse("2026-03-08");
        assertEquals(newYork.bucketOf(day, ReportBucket.DAY), newYork.between(day, day));
    }

    @Test
    void rejectsAReversedOrIncompleteRange() {
        LocalDate day = LocalDate.parse("2026-01-15");
        assertThrows(IllegalArgumentException.class, () -> newYork.between(day, day.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> newYork.between(null, day));
        assertThrows(IllegalArgumentException.class, () -> newYork.between(day, null));
    }
}
