package com.lemarketjames.reports.overnight;

import com.lemarketjames.config.ReportingDataSourceConfig;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Refreshes the nightly trade summary, {@code reporting_daily_trades} (migration 030), the separated
 * read side of the long-range reports (BR-16). Overnight only: {@link MonthlyTradingSummaryJob}
 * calls it before it writes its file.
 *
 * <p>Like {@link ReportRunWriter}, it writes through the reports' read-only-by-default pool by making
 * its own transaction read-write (README, rule 5), and it reads trades only from the
 * {@code reporting_trades} view (rule 2). It raises the statement timeout for its own transaction
 * only: the first night summarizes all history, which the daytime 5-second limit is not sized for.
 */
@Component
public class DailySummaryWriter {

    private final JdbcTemplate jdbc;
    private final ReportCalendar calendar;
    private final Clock clock;
    private final long statementTimeoutMs;

    public DailySummaryWriter(@Qualifier(ReportingDataSourceConfig.JDBC) JdbcTemplate jdbc, ReportCalendar calendar,
                              Clock clock, @Value("${reporting.overnight.statement-timeout-ms}") long statementTimeoutMs) {
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.clock = clock;
        this.statementTimeoutMs = statementTimeoutMs;
    }

    /**
     * Summarizes every day not summarized yet, through {@code runDate}, in the reports time zone, and
     * records that the summary now covers them. {@code runDate} itself is always rebuilt, so a second
     * run of the same night changes nothing. On the first night it starts at the first trade.
     *
     * @return the days rebuilt, both included
     */
    @Transactional(transactionManager = ReportingDataSourceConfig.TRANSACTION_MANAGER)
    public SummarizedDays refreshThrough(LocalDate runDate) {
        jdbc.execute("SET TRANSACTION READ WRITE");
        jdbc.execute("SET LOCAL statement_timeout = " + statementTimeoutMs);
        String zone = calendar.zone().getId();

        // A lock on the zone's row, so two runs never rebuild the same days at once.
        List<LocalDate> through = jdbc.queryForList(
                "SELECT summarized_through FROM reporting_summary_state WHERE time_zone = ? FOR UPDATE", LocalDate.class, zone);
        LocalDate from = through.isEmpty() ? firstTradeDay(zone, runDate) : earliest(through.get(0).plusDays(1), runDate);
        ReportPeriod days = calendar.between(from, runDate);

        jdbc.update("DELETE FROM reporting_daily_trades WHERE time_zone = ? AND trade_date BETWEEN ? AND ?", zone, from, runDate);
        // The view's own columns, grouped by the trade's day in the reports time zone: the same day
        // boundaries every live report takes from ReportCalendar (rule 4).
        jdbc.update("""
                INSERT INTO reporting_daily_trades (time_zone, trade_date, symbol, segment, side, trade_count, quantity, gross_amount)
                SELECT ?, ((filled_at AT TIME ZONE 'UTC') AT TIME ZONE ?)::date, symbol, segment, side,
                       COUNT(*), SUM(quantity), SUM(gross_amount)
                FROM reporting_trades
                WHERE filled_at >= ? AND filled_at < ?
                GROUP BY 2, 3, 4, 5
                """, zone, zone, days.start(), days.end());
        // Never backwards: a catch-up of an older night leaves the later days covered.
        jdbc.update("""
                INSERT INTO reporting_summary_state (time_zone, summarized_through, refreshed_at) VALUES (?, ?, ?)
                ON CONFLICT (time_zone) DO UPDATE
                SET summarized_through = GREATEST(reporting_summary_state.summarized_through, EXCLUDED.summarized_through),
                    refreshed_at = EXCLUDED.refreshed_at
                """, zone, runDate, clock.instant().atOffset(ZoneOffset.UTC));
        return new SummarizedDays(from, runDate);
    }

    /** The days a refresh rebuilt, both included. */
    public record SummarizedDays(LocalDate from, LocalDate to) {
    }

    /** The reports-time-zone day of the first trade, or {@code runDate} when there is none before it. */
    private LocalDate firstTradeDay(String zone, LocalDate runDate) {
        LocalDateTime first = jdbc.queryForObject("SELECT MIN(filled_at) FROM reporting_trades", LocalDateTime.class);
        return first == null ? runDate : earliest(calendar.reportDay(first.toInstant(ZoneOffset.UTC)), runDate);
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
