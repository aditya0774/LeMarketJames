package com.lemarketjames.trades;

import com.lemarketjames.orders.entity.Order;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** A read-only projection keeps staff search separate from order execution and client history. */
public interface TradeSearchRepository extends Repository<Order, Integer> {
    @Query("""
        SELECT new com.lemarketjames.trades.TradeSearchResult(
            o.orderId, a.clientId, o.accountId, o.instrumentId, i.ticker,
            o.orderType, o.quantity, o.pricePerUnit, o.filledAt)
        FROM Order o JOIN AccountEntity a ON a.accountId = o.accountId
        JOIN Instrument i ON i.instrumentId = o.instrumentId
        WHERE o.orderStatus = :status AND o.orderId = :orderId
        """)
    List<TradeSearchResult> findTrade(@Param("orderId") Integer orderId,
            @Param("status") Order.OrderStatus status);

    @Query("""
        SELECT new com.lemarketjames.trades.TradeSearchResult(
            o.orderId, a.clientId, o.accountId, o.instrumentId, i.ticker,
            o.orderType, o.quantity, o.pricePerUnit, o.filledAt)
        FROM Order o JOIN AccountEntity a ON a.accountId = o.accountId
        JOIN Instrument i ON i.instrumentId = o.instrumentId
        WHERE o.orderStatus = :status AND a.clientId = :clientId
        AND o.filledAt >= :start AND o.filledAt < :end
        ORDER BY o.filledAt DESC, o.orderId DESC
        """)
    List<TradeSearchResult> findClientTrades(@Param("clientId") Integer clientId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
            @Param("status") Order.OrderStatus status);
}
