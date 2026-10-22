package com.lemarketjames.reports;

import com.lemarketjames.reports.dto.TradesByStockReportRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.TradesReportRepository;
import com.lemarketjames.reports.summary.DailyTradeSummary;
import com.lemarketjames.reports.summary.ReadSplit;
import com.lemarketjames.reports.summary.SymbolTotals;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating aggregate trading reports.
 * All queries are read-only; this service is fully isolated from trading and account services.
 * Days the nightly summary covers are read from it ({@link DailyTradeSummary}), later days from the
 * reporting_trades view, so a long range does not scan live orders (BR-16, README "Read side").
 */
@Service
public class TradesReportService {

    private final TradesReportRepository repository;
    private final DailyTradeSummary summary;
    private final ReportCalendar reportCalendar;

    public TradesReportService(TradesReportRepository repository, DailyTradeSummary summary, ReportCalendar reportCalendar) {
        this.repository = repository;
        this.summary = summary;
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
        
        String zone = reportCalendar.zone().getId();
        ReadSplit split = ReadSplit.of(startDate, endDate, summary.summarizedThrough(zone));
        List<SymbolTotals> rows = new ArrayList<>();
        split.summarized().ifPresent(days -> rows.addAll(summary.bySymbol(zone, days.from(), days.to())));
        split.live().ifPresent(days -> {
            // Convert to UTC period using ReportCalendar for timezone-aware boundaries
            ReportPeriod period = reportCalendar.between(days.from(), days.to());
            repository.aggregateTradesBySymbol(period.start(), period.end()).forEach(projection -> rows.add(new SymbolTotals(
                    projection.getSymbol(), projection.getTotalQuantity(), projection.getTotalGrossAmount(),
                    projection.getBuyCount(), projection.getSellCount())));
        });

        // A stock traded on both sides of the summary's last day is added up here.
        return SymbolTotals.merge(rows).stream()
                .map(totals -> new TradesByStockReportRow(totals.symbol(), totals.totalQuantity(),
                        totals.totalGrossAmount(), totals.buyCount(), totals.sellCount()))
                .toList();
    }
}
