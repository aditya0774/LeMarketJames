package com.lemarketjames.orders.events;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;

import java.time.Instant;

/**
 * Published inside buy-sell-service after every order status transition (contract C6). Creation isn't a
 * transition: a new order is always SUBMITTED, and {@link OrderSubmitted} announces it. Listen with
 * {@code @EventListener}, or with {@code @TransactionalEventListener} to act only once the
 * transition has committed.
 *
 * @param orderId         the order
 * @param accountId       the order's account
 * @param from            the status before the transition
 * @param to              the status after it
 * @param occurredAt      when the transition happened (UTC)
 * @param rejectionReason why the order was rejected; null unless {@code to} is REJECTED. Added
 *                        after the other fields, so a consumer that doesn't name it is unaffected
 */
public record OrderStatusChanged(Integer orderId, Integer accountId, OrderStatus from, OrderStatus to,
                                 Instant occurredAt, RejectionReason rejectionReason) {
}
