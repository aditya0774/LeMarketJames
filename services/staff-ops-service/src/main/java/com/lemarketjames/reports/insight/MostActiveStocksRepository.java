package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.entity.ReportingTrade;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Aggregates the reporting view without exposing individual trades. */
public interface MostActiveStocksRepository extends Repository<ReportingTrade, Integer> {

    @Query(nativeQuery = true, value = """
            SELECT symbol AS "symbol",
                   CAST(COALESCE(SUM(gross_amount), 0) AS NUMERIC) AS "tradedValue",
                   COUNT(*) AS "tradeCount",
                   CAST(COALESCE(SUM(quantity), 0) AS NUMERIC) AS "shareVolume"
            FROM reporting_trades
            WHERE filled_at >= :startUtc AND filled_at < :endUtc
            GROUP BY symbol
            ORDER BY SUM(gross_amount) DESC, symbol ASC
            LIMIT 5
            """)
    List<MostActiveStockAggregate> findTopFive(@Param("startUtc") LocalDateTime startUtc,
                                               @Param("endUtc") LocalDateTime endUtc);

    /**
     * Traded value per stock in a range, for the given stocks only: what this week's top five are
     * compared with. A stock that did not trade in the range has no row.
     */
    @Query(nativeQuery = true, value = """
            SELECT symbol AS "symbol",
                   CAST(COALESCE(SUM(gross_amount), 0) AS NUMERIC) AS "tradedValue"
            FROM reporting_trades
            WHERE filled_at >= :startUtc AND filled_at < :endUtc
              AND symbol IN (:symbols)
            GROUP BY symbol
            """)
    List<SymbolTradedValue> tradedValueOf(@Param("symbols") Collection<String> symbols,
                                          @Param("startUtc") LocalDateTime startUtc,
                                          @Param("endUtc") LocalDateTime endUtc);

    interface MostActiveStockAggregate {
        String getSymbol();

        java.math.BigDecimal getTradedValue();

        long getTradeCount();

        java.math.BigDecimal getShareVolume();
    }

    interface SymbolTradedValue {
        String getSymbol();

        java.math.BigDecimal getTradedValue();
    }
}