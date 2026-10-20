package com.lemarketjames.reports.repository;

import java.math.BigDecimal;

/**
 * Projection for aggregated trades by stock symbol.
 * Spring Data uses this to map native SQL query results to the DTO.
 */
public interface TradesAggregateProjection {
    String getSymbol();
    BigDecimal getTotalQuantity();
    BigDecimal getTotalGrossAmount();
    long getBuyCount();
    long getSellCount();
}
