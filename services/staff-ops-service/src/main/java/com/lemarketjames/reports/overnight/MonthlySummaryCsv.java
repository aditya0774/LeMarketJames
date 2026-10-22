package com.lemarketjames.reports.overnight;

import com.lemarketjames.reports.summary.DailyTradeSummary.StockSegmentTotals;

import java.time.LocalDate;
import java.util.List;

/**
 * Writes the monthly trading summary as CSV: one line per stock and client segment, money in USD
 * (the platform's only currency, BR-12). Aggregates only; nothing identifies a client (README, rule 3).
 */
final class MonthlySummaryCsv {

    static final String CONTENT_TYPE = "text/csv";
    static final String HEADER = "symbol,segment,trades,buys,sells,shares,gross_amount_usd";

    private MonthlySummaryCsv() {
    }

    /** {@code monthly-trading-summary-2026-10-01-to-2026-10-20.csv}: the days it covers, both included. */
    static String fileName(LocalDate from, LocalDate to) {
        return "monthly-trading-summary-" + from + "-to-" + to + ".csv";
    }

    static String write(List<StockSegmentTotals> rows) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (StockSegmentTotals row : rows) {
            csv.append(field(row.symbol())).append(',')
                    .append(field(row.segment())).append(',')
                    .append(row.tradeCount()).append(',')
                    .append(row.buyCount()).append(',')
                    .append(row.sellCount()).append(',')
                    .append(row.quantity().stripTrailingZeros().toPlainString()).append(',')
                    // Cents, as the money is shown everywhere else; the summary keeps the exact sum.
                    .append(row.grossAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                    .append("\r\n");
        }
        return csv.toString();
    }

    /**
     * RFC 4180 quoting when a value needs it. A value a spreadsheet would run as a formula (one that
     * starts with =, +, - or @) gets a leading apostrophe; symbols and segments never do today.
     */
    static String field(String value) {
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.matches(".*[\",\r\n].*") ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }
}
