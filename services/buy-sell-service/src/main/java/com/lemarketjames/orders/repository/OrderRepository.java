package com.lemarketjames.orders.repository;

import com.lemarketjames.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :id")
    java.util.Optional<Order> findLockedById(@Param("id") Integer id);

    @Query("SELECT o.orderId FROM Order o WHERE o.orderStatus IN :statuses ORDER BY o.orderId")
    List<Integer> findExecutionCandidates(@Param("statuses") List<Order.OrderStatus> statuses);

    // Half-open bounds include the first instant and exclude the next calendar period.
    @Query("""
        SELECT o FROM Order o WHERE o.accountId = :accountId
        AND (:status IS NULL OR o.orderStatus = :status)
        AND o.submittedAt >= :start AND o.submittedAt < :end
        """)
    List<Order> findHistory(@Param("accountId") Integer accountId,
        @Param("status") Order.OrderStatus status,
        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Find all orders for a specific account
     */
    List<Order> findByAccountId(Integer accountId);
    
    /**
     * Find all orders for a specific account with a given status
     */
    List<Order> findByAccountIdAndOrderStatus(Integer accountId, Order.OrderStatus orderStatus);
    
    /**
     * Find all orders for a specific instrument
     */
    List<Order> findByInstrumentId(Integer instrumentId);
    @Query("""
        SELECT o FROM Order o JOIN AccountEntity a ON a.accountId = o.accountId
        JOIN ClientEntity c ON c.clientId = a.clientId
        WHERE o.instrumentId = :instrumentId AND c.username = :username
        """)
    List<Order> findOwnByInstrumentId(@Param("instrumentId") Integer instrumentId,
        @Param("username") String username);
}
