package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.exception.InvalidStatusTransitionException;
import com.lemarketjames.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional half of execution.
 * Separate bean ensures the durable intent commits BEFORE any remote settlement call.
 */
@Service
public class OrderExecutionTransactions {
    private final OrderRepository orders;
    private final OrderExecutor executor;
    private final OrderTransitions transitions;
    public OrderExecutionTransactions(OrderRepository orders, OrderExecutor executor, OrderTransitions transitions) {
        this.orders = orders; this.executor = executor; this.transitions = transitions;
    }
    @Transactional
    public Order prepare(int id, boolean manual) {
        Order order = orders.findLockedById(id).orElseThrow();
        // Retry path: if settlement was already in-flight, reuse that exact persisted intent.
        if (order.isSettlementPending()) return order;
        if (!order.getOrderStatus().isOpen()) {
            if (manual) throw new InvalidStatusTransitionException(order.getOrderStatus(), OrderStatus.FILLED);
            return null;
        }
        if (order.getOrderStatus() == OrderStatus.SUBMITTED) {
            if (manual) throw new InvalidStatusTransitionException(OrderStatus.SUBMITTED, OrderStatus.FILLED);
            transitions.move(order, OrderStatus.ACCEPTED, null);
        }
        ExecutionResult result;
        try {
            result = executor.execute(order);
        } catch (RuntimeException failure) {
            if (order.getOrderStatus() != OrderStatus.PENDING) {
                transitions.move(order, OrderStatus.PENDING, null);
            }
            return null;
        }
        if (result.nextStatus() == OrderStatus.FILLED) {
            if (order.getOrderStatus() != OrderStatus.PENDING) transitions.move(order, OrderStatus.PENDING, null);
            // Persist fill quote before remote settlement so retries never re-price a pending order.
            order.setPricePerUnit(result.fillPrice());
            order.setQuoteSource(result.quote().source());
            order.setQuoteTime(result.quote().quoteTime());
            order.setSettlementPending(true);
            return order;
        }
        if (order.getOrderStatus() != result.nextStatus()) {
            transitions.move(order, result.nextStatus(), result.reason(), result.quote());
        }
        return null;
    }
    @Transactional
    public void finish(int id, RejectionReason rejection) {
        Order order = orders.findLockedById(id).orElseThrow();
        // Idempotent completion: if another worker already reconciled, do nothing.
        if (!order.isSettlementPending()) return;
        transitions.move(order, rejection == null ? OrderStatus.FILLED : OrderStatus.REJECTED, rejection);
        order.setSettlementPending(false);
    }
}
