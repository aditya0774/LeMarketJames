package com.lemarketjames.orders.repository;

import com.lemarketjames.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
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
}
