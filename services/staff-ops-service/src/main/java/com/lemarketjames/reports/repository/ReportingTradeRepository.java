package com.lemarketjames.reports.repository;

import com.lemarketjames.reports.entity.ReportingTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access layer for querying settled trades from the reporting_trades view.
 * Aggregates in SQL rather than returning trades, so reports never stream the whole view (LMKT-44).
 */
@Repository
public interface ReportingTradeRepository extends JpaRepository<ReportingTrade, Integer> {

    /**
     * Counts and totals per period, grouped in the database (LMKT-44). Only one row per period
     * leaves PostgreSQL, so a report over a year of trades holds its pooled connection briefly
     * instead of streaming every trade to this service, which slowed live order execution.
     *
     * <p>{@code filled_at} is stored in UTC without an offset; it is read as UTC, then as local time
     * in the reports time zone, before {@code date_trunc} finds the period's first day. Weeks start
     * on Monday, as {@link com.lemarketjames.reports.period.ReportBucket#WEEK} does.
     *
     * @param unit     the {@code date_trunc} unit: {@code day}, {@code week}, {@code month} or {@code year}
     * @param zone     IANA ID of the reports time zone
     * @param startUtc inclusive start of the range (UTC)
     * @param endUtc   exclusive end of the range (UTC)
     * @return one row per period that has trades, oldest first
     */
    @Query(nativeQuery = true, value = """
            SELECT to_char(date_trunc(:unit, (filled_at AT TIME ZONE 'UTC') AT TIME ZONE :zone), 'YYYY-MM-DD')
                       AS "periodStart",
                   COUNT(*) AS "tradeCount",
                   COUNT(*) FILTER (WHERE side = 'BUY') AS "buyCount",
                   COUNT(*) FILTER (WHERE side = 'SELL') AS "sellCount",
                   COALESCE(SUM(gross_amount), 0) AS "totalValue"
            FROM reporting_trades
            WHERE filled_at >= :startUtc AND filled_at < :endUtc
            GROUP BY 1
            ORDER BY 1
            """)
    List<PeriodTotalsProjection> aggregateByPeriod(@Param("unit") String unit,
                                                   @Param("zone") String zone,
                                                   @Param("startUtc") LocalDateTime startUtc,
                                                   @Param("endUtc") LocalDateTime endUtc);
}
