package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.entity.ReportingTrade;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Counts the clients who traded in one range, from the {@code reporting_trades} view. It returns
 * only a number, so no client identifier can leave the repository. Extends the marker
 * {@link Repository} so no trade-by-trade {@code findAll} is exposed.
 */
public interface ActiveClientTrendRepository extends Repository<ReportingTrade, Integer> {

    /**
     * Distinct clients with at least one filled trade in the half-open range {@code [startUtc, endUtc)},
     * counted across all of a client's accounts. An empty range yields {@code 0}.
     */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(DISTINCT client_id)
            FROM reporting_trades
            WHERE filled_at >= :startUtc AND filled_at < :endUtc
            """)
    long countActiveClientsBetween(@Param("startUtc") LocalDateTime startUtc,
                                   @Param("endUtc") LocalDateTime endUtc);
}
