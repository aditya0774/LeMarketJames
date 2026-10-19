package com.lemarketjames.reports.dto;

import java.math.BigDecimal;

/**
 * A single row in the aggregate trades-by-stock report.
 * Represents the total trading activity for one stock symbol across all clients.
 * No individual client, account, or order IDs are included (aggregate only).
 */
public record TradesByStockReportRow(
    String symbol,
    BigDecimal totalQuantity,
    BigDecimal totalGrossAmount,
    long buyCount,
    long sellCount
) {
}
