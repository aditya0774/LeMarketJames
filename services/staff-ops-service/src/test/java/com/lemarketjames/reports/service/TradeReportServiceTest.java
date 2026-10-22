package com.lemarketjames.reports.service;

import com.lemarketjames.reports.dto.PeriodAggregation;
import com.lemarketjames.reports.dto.TradeReportRequest;
import com.lemarketjames.reports.dto.TradeReportResponse;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.PeriodTotalsProjection;
import com.lemarketjames.reports.repository.ReportingTradeRepository;
import com.lemarketjames.reports.summary.DailyTradeSummary;
import com.lemarketjames.reports.summary.PeriodTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TradeReportService. The grouping itself runs in SQL
 * (ReportingTradeRepository.aggregateByPeriod), so these check what the service owns: the
 * request validation, what it asks the database for, and how each period row is labelled.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    @Mock
    private ReportingTradeRepository tradeRepository;

    @Mock
    private ReportCalendar reportCalendar;

    @Mock
    private Clock clock;

    @Mock
    private DailyTradeSummary summary;

    private TradeReportService reportService;

    /** A row as the database returns it. */
    private record Row(String getPeriodStart, long getTradeCount, long getBuyCount, long getSellCount,
                       BigDecimal getTotalValue) implements PeriodTotalsProjection {
    }

    @BeforeEach
    void setUp() {
        when(reportCalendar.zone()).thenReturn(ZONE);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-06T21:00:00Z"));
        when(clock.getZone()).thenReturn(ZONE);

        // Mock reportCalendar.between() to return a ReportPeriod
        when(reportCalendar.between(any(LocalDate.class), any(LocalDate.class)))
            .thenAnswer(invocation -> {
                LocalDate firstDay = invocation.getArgument(0);
                LocalDate lastDay = invocation.getArgument(1);

                // Validate just like the real method does
                if (firstDay == null || lastDay == null) {
                    throw new IllegalArgumentException("Both the first and the last day of the report are required");
                }
                if (lastDay.isBefore(firstDay)) {
                    throw new IllegalArgumentException("The report's last day must not be before its first day");
                }

                // Create ReportPeriod like the real calendar would
                return new ReportPeriod(firstDay, startInUtc(firstDay), startInUtc(lastDay.plusDays(1)));
            });

        // No summary yet (summarizedThrough is empty by default): every day is read live, as before BR-16.
        reportService = new TradeReportService(tradeRepository, summary, reportCalendar, clock);
    }

    @Test
    void testDailyAggregation() {
        // Given: two days with trades, as the database groups them
        mockRows(
            new Row("2026-10-01", 2, 1, 1, new BigDecimal("1600.00")),
            new Row("2026-10-02", 1, 1, 0, new BigDecimal("287.50")));

        // When: Generating a daily report
        TradeReportRequest request = new TradeReportRequest("DAY",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2),
            "America/New_York");
        TradeReportResponse response = reportService.generateReport(request);

        // Then: Two periods with correct counts and values
        assertEquals(2, response.getData().size());

        PeriodAggregation oct1Agg = response.getData().get(0);
        assertEquals("2026-10-01", oct1Agg.getPeriod());
        assertEquals(2, oct1Agg.getTradeCount());
        assertEquals(1, oct1Agg.getBuyCount());
        assertEquals(1, oct1Agg.getSellCount());
        assertEquals(new BigDecimal("1600.00"), oct1Agg.getTotalValue());

        PeriodAggregation oct2Agg = response.getData().get(1);
        assertEquals("2026-10-02", oct2Agg.getPeriod());
        assertEquals(1, oct2Agg.getTradeCount());
        assertEquals(1, oct2Agg.getBuyCount());
        assertEquals(0, oct2Agg.getSellCount());
        assertEquals(new BigDecimal("287.50"), oct2Agg.getTotalValue());
    }

    @Test
    void testWeeklyAggregation() {
        // Given: two weeks, each starting on its Monday
        mockRows(
            new Row("2026-10-05", 2, 1, 1, new BigDecimal("1600.00")),
            new Row("2026-10-12", 1, 1, 0, new BigDecimal("287.50")));

        // When: Generating a weekly report
        TradeReportRequest request = new TradeReportRequest("WEEK",
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 12),
            "America/New_York");
        TradeReportResponse response = reportService.generateReport(request);

        // Then: Two weeks
        assertEquals(2, response.getData().size());
        assertEquals("2026-W41", response.getData().get(0).getPeriod());
        assertEquals("2026-W42", response.getData().get(1).getPeriod());
    }

    @Test
    void testMonthlyAggregation() {
        // Given: two months
        mockRows(
            new Row("2026-09-01", 1, 1, 0, new BigDecimal("1000.00")),
            new Row("2026-10-01", 1, 0, 1, new BigDecimal("600.00")));

        // When: Generating a monthly report
        TradeReportRequest request = new TradeReportRequest("MONTH",
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 1),
            "America/New_York");
        TradeReportResponse response = reportService.generateReport(request);

        // Then: Two months
        assertEquals(2, response.getData().size());
        assertEquals("2026-09", response.getData().get(0).getPeriod());
        assertEquals("2026-10", response.getData().get(1).getPeriod());
    }

    @Test
    void testYearlyAggregation() {
        // Given: two years
        mockRows(
            new Row("2025-01-01", 1, 1, 0, new BigDecimal("1000.00")),
            new Row("2026-01-01", 1, 0, 1, new BigDecimal("600.00")));

        // When: Generating a yearly report
        TradeReportRequest request = new TradeReportRequest("YEAR",
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 1),
            "America/New_York");
        TradeReportResponse response = reportService.generateReport(request);

        // Then: Two years
        assertEquals(2, response.getData().size());
        assertEquals("2025", response.getData().get(0).getPeriod());
        assertEquals("2026", response.getData().get(1).getPeriod());
    }

    @Test
    void asksTheDatabaseToGroupByTheBucketInTheReportZone() {
        mockRows();

        reportService.generateReport(new TradeReportRequest("WEEK",
            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), null));

        // date_trunc's unit, the zone's IANA ID, and the calendar's half-open UTC range.
        verify(tradeRepository).aggregateByPeriod("week", "America/New_York",
            startInUtc(LocalDate.of(2026, 10, 5)), startInUtc(LocalDate.of(2026, 10, 13)));
    }

    @Test
    void noTradesGivesAnEmptyReport() {
        mockRows();

        TradeReportResponse response = reportService.generateReport(new TradeReportRequest("DAY",
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), null));

        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testInvalidPeriodType() {
        // Given: Invalid period type
        TradeReportRequest request = new TradeReportRequest("INVALID",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2),
            "America/New_York");

        // When/Then: Throws exception
        assertThrows(IllegalArgumentException.class, () -> reportService.generateReport(request));
    }

    @Test
    void testMissingPeriodType() {
        // Given: Missing period type
        TradeReportRequest request = new TradeReportRequest(null,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2),
            "America/New_York");

        // When/Then: Throws exception
        assertThrows(IllegalArgumentException.class, () -> reportService.generateReport(request));
    }

    @Test
    void testFromAfterToThrowsException() {
        // Given: from > to
        TradeReportRequest request = new TradeReportRequest("DAY",
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 1),
            "America/New_York");

        // When/Then: Throws exception
        assertThrows(IllegalArgumentException.class, () -> reportService.generateReport(request));
    }

    /** Helper: the database returns these rows for any request. */
    // BR-16: days the nightly summary covers come from it; only later days are read live, and a
    // week on both sides of the summary's last day is added up once.
    @Test
    void summarizedDaysComeFromTheSummaryAndOnlyLaterDaysAreReadLive() {
        when(summary.summarizedThrough("America/New_York")).thenReturn(java.util.Optional.of(LocalDate.of(2026, 10, 6)));
        when(summary.byPeriod("week", "America/New_York", LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 6)))
            .thenReturn(List.of(
                new PeriodTotals("2026-09-28", 3, 2, 1, new BigDecimal("300.00")),
                new PeriodTotals("2026-10-05", 2, 1, 1, new BigDecimal("200.00"))));
        mockRows(new Row("2026-10-05", 1, 1, 0, new BigDecimal("50.25")));

        TradeReportResponse response = reportService.generateReport(new TradeReportRequest("WEEK",
            LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 8), "America/New_York"));

        verify(tradeRepository).aggregateByPeriod("week", "America/New_York",
            startInUtc(LocalDate.of(2026, 10, 7)), startInUtc(LocalDate.of(2026, 10, 9)));
        assertEquals(List.of("2026-W40", "2026-W41"), response.getData().stream().map(PeriodAggregation::getPeriod).toList());
        assertEquals(3, response.getData().get(1).getTradeCount());
        assertEquals(2, response.getData().get(1).getBuyCount());
        assertEquals(new BigDecimal("250.25"), response.getData().get(1).getTotalValue());
    }

    private void mockRows(PeriodTotalsProjection... rows) {
        when(tradeRepository.aggregateByPeriod(anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(rows));
    }

    private static LocalDateTime startInUtc(LocalDate day) {
        return LocalDateTime.ofInstant(day.atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC);
    }
}
