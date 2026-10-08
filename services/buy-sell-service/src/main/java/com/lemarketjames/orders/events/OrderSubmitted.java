package com.lemarketjames.orders.events;

import com.lemarketjames.orders.entity.Order.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published inside buy-sell-service when a new order passes its placement checks and is saved
 * (contract C6). It is the first event of every order: placement isn't a status transition, so no
 * {@link OrderStatusChanged} announces it, and without this a consumer would first hear of an order
 * when it is accepted, with nothing to say what was ordered. A refused submission is never saved, so
 * it has no order to announce; it is in the audit trail only (contract C2).
 *
 * @param orderId      the order
 * @param accountId    the order's account
 * @param instrumentId the stock ordered
 * @param side         BUY or SELL
 * @param quantity     shares ordered
 * @param price        price per share the order was placed at; null for a SELL. A fill takes the
 *                     price of the quote it executes at, which {@link OrderFilled} carries.
 * @param submittedAt  when the order was placed (UTC)
 */
public record OrderSubmitted(Integer orderId, Integer accountId, Integer instrumentId, OrderType side,
                             BigDecimal quantity, BigDecimal price, Instant submittedAt) {
}
