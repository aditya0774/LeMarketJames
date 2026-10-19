package com.lemarketjames.reports;

import com.lemarketjames.reports.dto.TradesByStockReportRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.TradesAggregateProjection;
import com.lemarketjames.reports.repository.TradesReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TradesReportService.
 * Verifies mapping from repository projections to DTOs, date range filtering, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class TradesReportServiceTest {

    @Mock
    private TradesReportRepository repository;

    @Mock
    private ReportCalendar reportCalendar;

    @InjectMocks
    private TradesReportService service;

    @Test
    void mapsAggregateProjectionsToDto() {
        // Arrange: Mock projection data for two stocks
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 7);
        LocalDateTime startUtc = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime endUtc = LocalDateTime.of(2026, 10, 8, 0, 0);
        ReportPeriod period = new ReportPeriod(startDate, startUtc, endUtc);

        when(reportCalendar.between(startDate, endDate)).thenReturn(period);

        TradesAggregateProjection aapl = mockProjection("AAPL", 150, "22500.75", 8, 5);
        TradesAggregateProjection msft = mockProjection("MSFT", 200, "50200.00", 10, 3);
        when(repository.aggregateTradesBySymbol(startUtc, endUtc)).thenReturn(List.of(aapl, msft));

        // Act
        List<TradesByStockReportRow> result = service.getTradesAggregateByStock(startDate, endDate);

        // Assert
        assertEquals(2, result.size());
        
        TradesByStockReportRow applRow = result.get(0);
        assertEquals("AAPL", applRow.symbol());
        assertEquals(new BigDecimal("150"), applRow.totalQuantity());
        assertEquals(new BigDecimal("22500.75"), applRow.totalGrossAmount());
        assertEquals(8, applRow.buyCount());
        assertEquals(5, applRow.sellCount());

        TradesByStockReportRow msftRow = result.get(1);
        assertEquals("MSFT", msftRow.symbol());
        assertEquals(new BigDecimal("200"), msftRow.totalQuantity());
        assertEquals(new BigDecimal("50200.00"), msftRow.totalGrossAmount());
        assertEquals(10, msftRow.buyCount());
        assertEquals(3, msftRow.sellCount());
    }

    @Test
    void returnsEmptyListWhenNoTradesExist() {
        // Arrange
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 7);
        LocalDateTime startUtc = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime endUtc = LocalDateTime.of(2026, 10, 8, 0, 0);
        ReportPeriod period = new ReportPeriod(startDate, startUtc, endUtc);

        when(reportCalendar.between(startDate, endDate)).thenReturn(period);
        when(repository.aggregateTradesBySymbol(startUtc, endUtc)).thenReturn(List.of());

        // Act
        List<TradesByStockReportRow> result = service.getTradesAggregateByStock(startDate, endDate);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void appliesDefaultsWhenDatesAreNull() {
        // Arrange: Service should apply defaults when dates are null
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDateTime startUtc = LocalDateTime.of(2026, 9, 7, 0, 0);
        LocalDateTime endUtc = LocalDateTime.of(2026, 10, 8, 0, 0);
        ReportPeriod period = new ReportPeriod(thirtyDaysAgo, startUtc, endUtc);

        when(reportCalendar.between(thirtyDaysAgo, today)).thenReturn(period);

        TradesAggregateProjection aapl = mockProjection("AAPL", 150, "22500.75", 8, 5);
        when(repository.aggregateTradesBySymbol(startUtc, endUtc)).thenReturn(List.of(aapl));

        // Act
        List<TradesByStockReportRow> result = service.getTradesAggregateByStock(null, null);

        // Assert
        assertEquals(1, result.size());
        verify(reportCalendar).between(thirtyDaysAgo, today);
    }

    @Test
    void throwsExceptionWhenStartDateIsAfterEndDate() {
        // Arrange
        LocalDate startDate = LocalDate.of(2026, 10, 7);
        LocalDate endDate = LocalDate.of(2026, 10, 1);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.getTradesAggregateByStock(startDate, endDate)
        );
        assertTrue(exception.getMessage().contains("Start date must not be after end date"));
    }

    @Test
    void handlesSingleStock() {
        // Arrange
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 7);
        LocalDateTime startUtc = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime endUtc = LocalDateTime.of(2026, 10, 8, 0, 0);
        ReportPeriod period = new ReportPeriod(startDate, startUtc, endUtc);

        when(reportCalendar.between(startDate, endDate)).thenReturn(period);

        TradesAggregateProjection googl = mockProjection("GOOGL", 50, "8500.50", 2, 1);
        when(repository.aggregateTradesBySymbol(startUtc, endUtc)).thenReturn(List.of(googl));

        // Act
        List<TradesByStockReportRow> result = service.getTradesAggregateByStock(startDate, endDate);

        // Assert
        assertEquals(1, result.size());
        TradesByStockReportRow row = result.get(0);
        assertEquals("GOOGL", row.symbol());
        assertEquals(new BigDecimal("50"), row.totalQuantity());
    }

    @Test
    void preservesOrderFromRepository() {
        // Arrange: Repository returns alphabetically sorted by symbol
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 7);
        LocalDateTime startUtc = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime endUtc = LocalDateTime.of(2026, 10, 8, 0, 0);
        ReportPeriod period = new ReportPeriod(startDate, startUtc, endUtc);

        when(reportCalendar.between(startDate, endDate)).thenReturn(period);

        TradesAggregateProjection aaa = mockProjection("AAA", 10, "100.00", 1, 0);
        TradesAggregateProjection zzz = mockProjection("ZZZ", 20, "500.00", 0, 1);
        TradesAggregateProjection mmm = mockProjection("MMM", 30, "300.00", 2, 1);
        when(repository.aggregateTradesBySymbol(startUtc, endUtc)).thenReturn(List.of(aaa, zzz, mmm));

        // Act
        List<TradesByStockReportRow> result = service.getTradesAggregateByStock(startDate, endDate);

        // Assert: Order matches repository order (which is sorted in the SQL query)
        assertEquals("AAA", result.get(0).symbol());
        assertEquals("ZZZ", result.get(1).symbol());
        assertEquals("MMM", result.get(2).symbol());
    }

    // Helper to create mock projections with fluent syntax
    private TradesAggregateProjection mockProjection(
            String symbol, 
            long quantity, 
            String grossAmount, 
            long buyCount, 
            long sellCount) {
        TradesAggregateProjection projection = mock(TradesAggregateProjection.class);
        when(projection.getSymbol()).thenReturn(symbol);
        when(projection.getTotalQuantity()).thenReturn(new BigDecimal(quantity));
        when(projection.getTotalGrossAmount()).thenReturn(new BigDecimal(grossAmount));
        when(projection.getBuyCount()).thenReturn(buyCount);
        when(projection.getSellCount()).thenReturn(sellCount);
        return projection;
    }
}
