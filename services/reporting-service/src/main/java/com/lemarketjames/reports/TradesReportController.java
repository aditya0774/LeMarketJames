package com.lemarketjames.reports;

import com.lemarketjames.reports.dto.TradesReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Report endpoint for aggregate trading activity by stock symbol.
 * ANALYST-only; role enforcement is handled by SecurityConfig at the path level.
 * Returns totals (quantity, gross amount) and counts (BUY/SELL) grouped by symbol.
 * No individual client, account, or order IDs are exposed.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class TradesReportController {

    private final TradesReportService tradesReportService;

    public TradesReportController(TradesReportService tradesReportService) {
        this.tradesReportService = tradesReportService;
    }

    /**
     * Get aggregate trades grouped by stock symbol, filtered by date range.
     * Aggregates all FILLED trades across all clients, grouped by symbol, sorted alphabetically.
     *
     * @param startDate Optional start date (inclusive, YYYY-MM-DD); defaults to 30 days ago
     * @param endDate Optional end date (inclusive, YYYY-MM-DD); defaults to today
     * @return Aggregate trades by symbol; 400 if dates are invalid, 403 if not ANALYST role, 401 if missing JWT
     */
    @GetMapping("/trades-by-stock")
    public TradesReportResponse tradesAggregateByStock(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        var data = tradesReportService.getTradesAggregateByStock(startDate, endDate);
        return new TradesReportResponse(true, data);
    }
}
