package com.lemarketjames.reports.summary;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One stock's trade totals over a range, from the summary or from the live view; a stock traded
 * on both sides of the summary's last day arrives twice and is {@link #merge merged}.
 */
public record SymbolTotals(String symbol, BigDecimal totalQuantity, BigDecimal totalGrossAmount, long buyCount, long sellCount) {

    SymbolTotals plus(SymbolTotals other) {
        return new SymbolTotals(symbol, totalQuantity.add(other.totalQuantity),
                totalGrossAmount.add(other.totalGrossAmount), buyCount + other.buyCount, sellCount + other.sellCount);
    }

    /** Adds up rows of the same stock; sorted by symbol, as the report is. */
    public static List<SymbolTotals> merge(Collection<SymbolTotals> rows) {
        Map<String, SymbolTotals> bySymbol = new TreeMap<>();
        rows.forEach(row -> bySymbol.merge(row.symbol(), row, SymbolTotals::plus));
        return List.copyOf(bySymbol.values());
    }
}
