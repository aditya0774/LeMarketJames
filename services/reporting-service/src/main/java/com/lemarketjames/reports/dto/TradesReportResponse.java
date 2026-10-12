package com.lemarketjames.reports.dto;

import java.util.List;

/**
 * Response wrapper for the trades-by-stock aggregate report.
 * Follows the pattern established by ReportPingController.PingResponse.
 */
public record TradesReportResponse(
    boolean success,
    List<TradesByStockReportRow> data
) {
}
