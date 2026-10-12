package com.lemarketjames.reports;

import com.lemarketjames.reports.dto.TradesByStockReportRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.TradesReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for generating aggregate trading reports.
 * All queries are read-only and hit the reporting_trades view directly;
 * this service is fully isolated from trading and account services.
 */
@Service
public class TradesReportService {

    private final TradesReportRepository repository;
    private final ReportCalendar reportCalendar;

    public TradesReportService(TradesReportRepository repository, ReportCalendar reportCalendar) {
        this.repository = repository;
        this.reportCalendar = reportCalendar;
    }

    /**
     * Aggregate all FILLED trades by stock symbol across all clients, filtered by date range.
     * Returns symbol totals (quantity, gross amount, and trade counts by side)
     * sorted alphabetically by symbol.
     *
     * @param startDate Optional start date (inclusive); defaults to 30 days ago
     * @param endDate Optional end date (inclusive); defaults to today
     * @return List of aggregated trades, one row per symbol; empty list if no trades exist
     * @throws IllegalArgumentException if startDate is after endDate
     */
    public List<TradesByStockReportRow> getTradesAggregateByStock(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        
        // Apply defaults
        if (startDate == null) {
            startDate = today.minusDays(30);
        }
        if (endDate == null) {
            endDate = today;
        }
        
        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }
        
        // Convert to UTC period using ReportCalendar for timezone-aware boundaries
        ReportPeriod period = reportCalendar.between(startDate, endDate);
        
        return repository.aggregateTradesBySymbol(period.start(), period.end()).stream()
                .map(projection -> new TradesByStockReportRow(
                        projection.getSymbol(),
                        projection.getTotalQuantity(),
                        projection.getTotalGrossAmount(),
                        projection.getBuyCount(),
                        projection.getSellCount()
                ))
                .toList();
    }
}
