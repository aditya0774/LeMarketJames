package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.exception.InvalidStatusTransitionException;
import com.lemarketjames.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Separate bean ensures the durable intent commits BEFORE any remote settlement call. */
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
        if (order.isSettlementPending()) return order;
        if (!order.getOrderStatus().isOpen()) {
            if (manual) throw new InvalidStatusTransitionException(order.getOrderStatus(), OrderStatus.FILLED);
            return null;
        }
        if (order.getOrderStatus() == OrderStatus.SUBMITTED) {
            if (manual) throw new InvalidStatusTransitionException(OrderStatus.SUBMITTED, OrderStatus.FILLED);
            transitions.move(order, OrderStatus.ACCEPTED, null);
        }
        ExecutionResult result = executor.execute(order);
        if (result.nextStatus() == OrderStatus.FILLED) {
            if (order.getOrderStatus() != OrderStatus.PENDING) transitions.move(order, OrderStatus.PENDING, null);
            order.setPricePerUnit(result.fillPrice());
            order.setSettlementPending(true);
            return order;
        }
        if (order.getOrderStatus() != result.nextStatus()) transitions.move(order, result.nextStatus(), result.reason());
        return null;
    }
    @Transactional
    public void finish(int id, RejectionReason rejection) {
        Order order = orders.findLockedById(id).orElseThrow();
        // Another worker may have completed the same idempotent request in the meantime.
        if (!order.isSettlementPending()) return;
        transitions.move(order, rejection == null ? OrderStatus.FILLED : OrderStatus.REJECTED, rejection);
        order.setSettlementPending(false);
    }
}
