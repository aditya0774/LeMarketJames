package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order;

/**
 * The execution interface (contract C6): decides whether and at what price an accepted order
 * fills. Only the interface is defined here; the market-price implementation belongs to the
 * "orders fill at market price" story. Callers move the order through its lifecycle with the
 * result, so the executor itself never changes order status or settles anything.
 */
public interface OrderExecutor {

    /**
     * Attempts to execute an order that is ACCEPTED, DELAYED or PENDING.
     *
     * @param order the order to execute; not modified
     * @return what happened
     */
    ExecutionResult execute(Order order);
}
