package com.lemarketjames.reports.service;

import com.lemarketjames.reports.dto.PeriodAggregation;
import com.lemarketjames.reports.dto.TradeReportRequest;
import com.lemarketjames.reports.dto.TradeReportResponse;
import com.lemarketjames.reports.entity.ReportingTrade;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.ReportingTradeRepository;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TradeReportService aggregation logic.
 * Tests period grouping, counting, and value aggregation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeReportServiceTest {

    @Mock
    private ReportingTradeRepository tradeRepository;

    @Mock
    private ReportCalendar reportCalendar;

    @Mock
    private Clock clock;

    private TradeReportService reportService;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("America/New_York");
        when(reportCalendar.zone()).thenReturn(zone);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-06T21:00:00Z"));
        when(clock.getZone()).thenReturn(zone);
        
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
                LocalDateTime startUtc = LocalDateTime.ofInstant(
                    firstDay.atStartOfDay(zone).toInstant(), 
                    ZoneOffset.UTC
                );
                LocalDateTime endUtc = LocalDateTime.ofInstant(
                    lastDay.plusDays(1).atStartOfDay(zone).toInstant(), 
                    ZoneOffset.UTC
                );
                return new ReportPeriod(firstDay, startUtc, endUtc);
            });
        
        reportService = new TradeReportService(tradeRepository, reportCalendar, clock);
    }

    @Test
    void testDailyAggregation() {
        // Given: Three trades on two different days
        LocalDateTime oct1 = LocalDateTime.of(2026, 10, 1, 10, 30, 0);
        LocalDateTime oct2 = LocalDateTime.of(2026, 10, 2, 14, 15, 0);

        List<ReportingTrade> trades = new ArrayList<>();
        trades.add(createTrade(1, "BUY", new BigDecimal("100"), "10.00", oct1));
        trades.add(createTrade(2, "SELL", new BigDecimal("50"), "12.00", oct1));
        trades.add(createTrade(3, "BUY", new BigDecimal("25"), "11.50", oct2));

        mockTradeRepository(trades);

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
        // Given: Trades in two different weeks
        LocalDateTime monday = LocalDateTime.of(2026, 10, 5, 10, 0, 0); // Week 41
        LocalDateTime tuesday = LocalDateTime.of(2026, 10, 6, 11, 0, 0);
        LocalDateTime nextMonday = LocalDateTime.of(2026, 10, 12, 10, 0, 0); // Week 42

        List<ReportingTrade> trades = new ArrayList<>();
        trades.add(createTrade(1, "BUY", new BigDecimal("100"), "10.00", monday));
        trades.add(createTrade(2, "SELL", new BigDecimal("50"), "12.00", tuesday));
        trades.add(createTrade(3, "BUY", new BigDecimal("25"), "11.50", nextMonday));

        mockTradeRepository(trades);

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
        // Given: Trades in two different months
        LocalDateTime sep30 = LocalDateTime.of(2026, 9, 30, 10, 0, 0);
        LocalDateTime oct1 = LocalDateTime.of(2026, 10, 1, 10, 0, 0);

        List<ReportingTrade> trades = new ArrayList<>();
        trades.add(createTrade(1, "BUY", new BigDecimal("100"), "10.00", sep30));
        trades.add(createTrade(2, "SELL", new BigDecimal("50"), "12.00", oct1));

        mockTradeRepository(trades);

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
        // Given: Trades in two different years
        LocalDateTime dec31_2025 = LocalDateTime.of(2025, 12, 31, 10, 0, 0);
        LocalDateTime jan1_2026 = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        List<ReportingTrade> trades = new ArrayList<>();
        trades.add(createTrade(1, "BUY", new BigDecimal("100"), "10.00", dec31_2025));
        trades.add(createTrade(2, "SELL", new BigDecimal("50"), "12.00", jan1_2026));

        mockTradeRepository(trades);

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

    /**
     * Helper: Mock repository to return trades for any date range.
     */
    private void mockTradeRepository(List<ReportingTrade> trades) {
        when(tradeRepository.findByFilledAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(trades);
    }

    /**
     * Helper: Create a ReportingTrade with minimal fields.
     */
    private ReportingTrade createTrade(Integer orderId, String side, BigDecimal quantity, String pricePerUnit, LocalDateTime filledAt) {
        ReportingTrade trade = new ReportingTrade();
        // Use reflection to set private fields for testing
        try {
            var field = ReportingTrade.class.getDeclaredField("orderId");
            field.setAccessible(true);
            field.set(trade, orderId);

            field = ReportingTrade.class.getDeclaredField("side");
            field.setAccessible(true);
            field.set(trade, side);

            field = ReportingTrade.class.getDeclaredField("quantity");
            field.setAccessible(true);
            field.set(trade, quantity);

            field = ReportingTrade.class.getDeclaredField("pricePerUnit");
            field.setAccessible(true);
            field.set(trade, new BigDecimal(pricePerUnit));

            field = ReportingTrade.class.getDeclaredField("grossAmount");
            field.setAccessible(true);
            field.set(trade, new BigDecimal(pricePerUnit).multiply(quantity));

            field = ReportingTrade.class.getDeclaredField("filledAt");
            field.setAccessible(true);
            field.set(trade, filledAt);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return trade;
    }
}
