package com.lemarketjames.reports.summary;

import com.lemarketjames.config.ReportingDataSourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reads the nightly trade summary, {@code reporting_daily_trades} (migration 030): the separated
 * read side for long-range reports (BR-16). One row per day, stock, segment and side, so a year of
 * trades is a few thousand rows however many trades there were. Built only by the overnight job
 * (MonthlyTradingSummaryJob), from the {@code reporting_trades} view.
 *
 * <p>Reads through the reports' pool like every report (README, rules 1 and 5). Every method
 * takes the reports time zone: the summary keeps days per zone, and only the current zone's count.
 */
@Component
public class DailyTradeSummary {

    private final JdbcTemplate jdbc;

    public DailyTradeSummary(@Qualifier(ReportingDataSourceConfig.JDBC) JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The last day the summary covers in this zone (every earlier day is covered too), or empty if none. */
    public Optional<LocalDate> summarizedThrough(String zone) {
        return jdbc.queryForList("SELECT summarized_through FROM reporting_summary_state WHERE time_zone = ?",
                LocalDate.class, zone).stream().findFirst();
    }

    /**
     * Totals per period, for the days {@code from} to {@code to} (both included).
     *
     * @param unit date_trunc's unit: day, week, month or year
     */
    public List<PeriodTotals> byPeriod(String unit, String zone, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT to_char(date_trunc(?, trade_date), 'YYYY-MM-DD') AS period_start,
                       SUM(trade_count) AS trades,
                       COALESCE(SUM(trade_count) FILTER (WHERE side = 'BUY'), 0) AS buys,
                       COALESCE(SUM(trade_count) FILTER (WHERE side = 'SELL'), 0) AS sells,
                       SUM(gross_amount) AS total_value
                FROM reporting_daily_trades
                WHERE time_zone = ? AND trade_date BETWEEN ? AND ?
                GROUP BY 1
                """,
                (rs, i) -> new PeriodTotals(rs.getString("period_start"), rs.getLong("trades"), rs.getLong("buys"),
                        rs.getLong("sells"), rs.getBigDecimal("total_value")),
                unit, zone, from, to);
    }

    /** Totals per stock for the days {@code from} to {@code to} (both included). */
    public List<SymbolTotals> bySymbol(String zone, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT symbol, SUM(quantity) AS quantity, SUM(gross_amount) AS gross_amount,
                       COALESCE(SUM(trade_count) FILTER (WHERE side = 'BUY'), 0) AS buys,
                       COALESCE(SUM(trade_count) FILTER (WHERE side = 'SELL'), 0) AS sells
                FROM reporting_daily_trades
                WHERE time_zone = ? AND trade_date BETWEEN ? AND ?
                GROUP BY symbol
                """,
                (rs, i) -> new SymbolTotals(rs.getString("symbol"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("gross_amount"), rs.getLong("buys"), rs.getLong("sells")),
                zone, from, to);
    }

    /** Totals per stock and client segment, for the overnight monthly summary; sorted by stock, then segment. */
    public List<StockSegmentTotals> byStockAndSegment(String zone, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT symbol, segment, SUM(trade_count) AS trades,
                       COALESCE(SUM(trade_count) FILTER (WHERE side = 'BUY'), 0) AS buys,
                       COALESCE(SUM(trade_count) FILTER (WHERE side = 'SELL'), 0) AS sells,
                       SUM(quantity) AS quantity, SUM(gross_amount) AS gross_amount
                FROM reporting_daily_trades
                WHERE time_zone = ? AND trade_date BETWEEN ? AND ?
                GROUP BY symbol, segment
                ORDER BY symbol, segment
                """,
                (rs, i) -> new StockSegmentTotals(rs.getString("symbol"), rs.getString("segment"), rs.getLong("trades"),
                        rs.getLong("buys"), rs.getLong("sells"), rs.getBigDecimal("quantity"), rs.getBigDecimal("gross_amount")),
                zone, from, to);
    }

    /** One stock and segment's totals over a range. Aggregates only: nothing identifies a client (README, rule 3). */
    public record StockSegmentTotals(String symbol, String segment, long tradeCount, long buyCount, long sellCount,
                                     BigDecimal quantity, BigDecimal grossAmount) {
    }
}
