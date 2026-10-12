package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.entity.ReportingTrade;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Reads the traded volume of one period from the {@code reporting_trades} view, the only data
 * source a report may use. Extends the marker {@link Repository} so that only this one aggregate
 * is exposed, not a trade-by-trade {@code findAll}.
 */
public interface VolumeInsightRepository extends Repository<ReportingTrade, Integer> {

    /**
     * Totals over the half-open range {@code [startUtc, endUtc)}. An empty range yields zeros,
     * not no row, so the caller never has to special-case a quiet period.
     */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)                                    AS "tradeCount",
                   CAST(COALESCE(SUM(quantity), 0) AS NUMERIC)     AS "shareVolume",
                   CAST(COALESCE(SUM(gross_amount), 0) AS NUMERIC) AS "grossAmount"
            FROM reporting_trades
            WHERE filled_at >= :startUtc AND filled_at < :endUtc
            """)
    VolumeTotals totalsBetween(@Param("startUtc") LocalDateTime startUtc,
                               @Param("endUtc") LocalDateTime endUtc);
}
