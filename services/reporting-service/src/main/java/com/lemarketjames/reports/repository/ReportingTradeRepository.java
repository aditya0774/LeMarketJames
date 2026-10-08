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
 * Supports filtering by filled date range for period-based aggregation.
 */
@Repository
public interface ReportingTradeRepository extends JpaRepository<ReportingTrade, Integer> {

    /**
     * Finds all trades filled within a half-open time range [startTime, endTime).
     *
     * Used by aggregation reports to group trades by period (day, week, month, year).
     * The view already filters to FILLED status only, so all returned rows are settled trades.
     *
     * @param startTime inclusive start of range (UTC)
     * @param endTime   exclusive end of range (UTC)
     * @return trades sorted by filled_at ascending
     */
    @Query(value = "SELECT * FROM reporting_trades WHERE filled_at >= :startTime AND filled_at < :endTime ORDER BY filled_at ASC",
           nativeQuery = true)
    List<ReportingTrade> findByFilledAtBetween(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);
}
