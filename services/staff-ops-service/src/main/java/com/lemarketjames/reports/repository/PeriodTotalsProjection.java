package com.lemarketjames.reports.repository;

import java.math.BigDecimal;

/**
 * One period's totals, as {@link ReportingTradeRepository#aggregateByPeriod} computes them in SQL.
 * Spring Data maps the native query's columns to these getters by name.
 */
public interface PeriodTotalsProjection {

    /** The period's first day in the reports time zone, as {@code YYYY-MM-DD}. */
    String getPeriodStart();

    long getTradeCount();

    long getBuyCount();

    long getSellCount();

    /** Sum of the trades' gross amounts (quantity × price); zero rather than null. */
    BigDecimal getTotalValue();
}
