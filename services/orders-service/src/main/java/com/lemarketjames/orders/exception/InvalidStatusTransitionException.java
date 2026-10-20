package com.lemarketjames.orders.exception;

import com.lemarketjames.orders.entity.Order.OrderStatus;

/** An attempt to move an order to a status its lifecycle doesn't allow from where it is (contract C1). */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Order cannot move from " + from + " to " + to);
    }
}
