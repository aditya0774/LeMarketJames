package com.lemarketjames.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Order entity.
 * Provides database operations: save, find, delete, update.
 * Extends JpaRepository to inherit pre-built CRUD methods.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
    /**
     * Find all orders for a specific account.
     * Spring automatically generates: SELECT * FROM orders WHERE account_id = ?
     * 
     * @param accountId the account ID
     * @return list of orders for that account
     */
    List<Order> findByAccountId(Integer accountId);
}
