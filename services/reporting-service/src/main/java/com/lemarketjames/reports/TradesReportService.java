package com.lemarketjames.reports;

import com.lemarketjames.reports.dto.TradesByStockReportRow;
import com.lemarketjames.reports.repository.TradesReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for generating aggregate trading reports.
 * All queries are read-only and hit the reporting_trades view directly;
 * this service is fully isolated from trading and account services.
 */
@Service
public class TradesReportService {

    private final TradesReportRepository repository;

    public TradesReportService(TradesReportRepository repository) {
        this.repository = repository;
    }

    /**
     * Aggregate all FILLED trades by stock symbol across all clients.
     * Returns symbol totals (quantity, gross amount, and trade counts by side)
     * sorted alphabetically by symbol.
     *
     * @return List of aggregated trades, one row per symbol; empty list if no trades exist
     */
    public List<TradesByStockReportRow> getTradesAggregateByStock() {
        return repository.aggregateTradesBySymbol().stream()
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
