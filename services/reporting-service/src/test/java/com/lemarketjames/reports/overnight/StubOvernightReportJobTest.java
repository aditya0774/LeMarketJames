package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The stub stands in for real overnight reports: it counts the run date's trades, or fails with the configured message. */
class StubOvernightReportJobTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-14");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportCalendar calendar = new ReportCalendar(new PlatformSettings());

    @Test
    void countsTheRunDatesTradesInTheReportsTimeZone() {
        // 2026-10-14 in New York (EDT) is 04:00 UTC that day to 04:00 UTC the next.
        LocalDateTime start = LocalDateTime.parse("2026-10-14T04:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-15T04:00:00");
        when(jdbc.queryForObject(contains("FROM reporting_trades"), eq(Long.class), eq(start), eq(end))).thenReturn(12L);

        assertDoesNotThrow(() -> new StubOvernightReportJob(jdbc, calendar, "").run(DAY));

        verify(jdbc).queryForObject(contains("FROM reporting_trades"), eq(Long.class), eq(start), eq(end));
    }

    @Test
    void failsWithTheConfiguredMessageWithoutReading() {
        var job = new StubOvernightReportJob(jdbc, calendar, "Stub failure");

        var failure = assertThrows(IllegalStateException.class, () -> job.run(DAY));

        assertEquals("Stub failure", failure.getMessage());
        verifyNoInteractions(jdbc);
    }
}
