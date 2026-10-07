package com.lemarketjames.reports.repository;

import com.lemarketjames.reports.domain.ReportingTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for reading trades from the reporting_trades view.
 * All queries are read-only aggregates; filtering and ownership validation happen in the service layer.
 */
@Repository
public interface TradesReportRepository extends JpaRepository<ReportingTrade, Long> {

    /**
     * Aggregate all FILLED trades by symbol across all clients.
     * Returns symbol, total quantity, total gross amount, and counts of BUY and SELL trades.
     *
     * @return List of aggregated trades sorted by symbol
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT 
                symbol,
                CAST(SUM(quantity) AS NUMERIC) AS totalQuantity,
                CAST(SUM(gross_amount) AS NUMERIC) AS totalGrossAmount,
                SUM(CASE WHEN side = 'BUY' THEN 1 ELSE 0 END) AS buyCount,
                SUM(CASE WHEN side = 'SELL' THEN 1 ELSE 0 END) AS sellCount
            FROM reporting_trades
            GROUP BY symbol
            ORDER BY symbol ASC
            """
    )
    List<TradesAggregateProjection> aggregateTradesBySymbol();
}
