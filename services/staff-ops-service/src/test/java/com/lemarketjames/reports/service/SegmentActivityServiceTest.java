package com.lemarketjames.reports.service;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.dto.SegmentActivityRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.repository.SegmentActivityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SegmentActivityServiceTest {
    private final SegmentActivityRepository repository = mock(SegmentActivityRepository.class);
    private final PlatformSettings settings = new PlatformSettings();
    private final ReportCalendar calendar = new ReportCalendar(settings);
    private final SegmentActivityService service = new SegmentActivityService(repository, calendar);

    @Test
    void convertsInclusiveDatesToHalfOpenUtcBoundariesAcrossDst() {
        var start = LocalDateTime.parse("2026-11-01T04:00:00");
        var end = LocalDateTime.parse("2026-11-02T05:00:00");
        var rows = List.of(new SegmentActivityRow("FUTURE_SEGMENT", 3, 8, new BigDecimal("50.1250")));
        when(repository.aggregate(start, end)).thenReturn(rows);
        assertEquals(rows, service.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)));
        verify(repository).aggregate(start, end);
    }

    @Test
    void omittedDatesUseTheReportZoneDefaults() {
        settings.getReports().setTimeZone(ZoneId.of("Pacific/Kiritimati"));
        LocalDate today = LocalDate.now(calendar.zone());
        var period = calendar.between(today.minusDays(30), today);
        service.report(null, null);
        verify(repository).aggregate(period.start(), period.end());
    }

    @Test
    void eitherBoundaryCanBeOmitted() {
        LocalDate today = LocalDate.now(calendar.zone());
        service.report(today.minusDays(2), null);
        var first = calendar.between(today.minusDays(2), today);
        verify(repository).aggregate(first.start(), first.end());
        service.report(null, today.minusDays(1));
        var second = calendar.between(today.minusDays(30), today.minusDays(1));
        verify(repository).aggregate(second.start(), second.end());
    }

    @Test
    void rejectsReversedDatesWithoutDatabaseWork() {
        assertThrows(IllegalArgumentException.class,
                () -> service.report(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
        verifyNoInteractions(repository);
    }
}
