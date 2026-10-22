package com.lemarketjames.reports.summary;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One period's trade totals, from the summary or from the live view. A period that spans both
 * (a week whose first days are summarized and whose last are live) arrives twice and is
 * {@link #merge merged}.
 *
 * @param periodStart the period's first day in the reports time zone, {@code YYYY-MM-DD}
 */
public record PeriodTotals(String periodStart, long tradeCount, long buyCount, long sellCount, BigDecimal totalValue) {

    PeriodTotals plus(PeriodTotals other) {
        return new PeriodTotals(periodStart, tradeCount + other.tradeCount, buyCount + other.buyCount,
                sellCount + other.sellCount, totalValue.add(other.totalValue));
    }

    /** Adds up rows of the same period; oldest period first. */
    public static List<PeriodTotals> merge(Collection<PeriodTotals> rows) {
        Map<String, PeriodTotals> byPeriod = new TreeMap<>();
        rows.forEach(row -> byPeriod.merge(row.periodStart(), row, PeriodTotals::plus));
        return List.copyOf(byPeriod.values());
    }
}
