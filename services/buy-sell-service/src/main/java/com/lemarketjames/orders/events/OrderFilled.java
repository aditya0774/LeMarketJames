package com.lemarketjames.orders.events;

import com.lemarketjames.orders.entity.Order.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published inside buy-sell-service when an order is filled and settled (contract C6), in addition to
 * its {@link OrderStatusChanged}. Carries what reporting and portfolio consumers need about the fill.
 *
 * @param orderId      the order
 * @param accountId    the order's account
 * @param instrumentId the stock traded
 * @param side         BUY or SELL
 * @param quantity     shares filled
 * @param price        price per share
 * @param filledAt     when the fill happened (UTC)
 */
public record OrderFilled(Integer orderId, Integer accountId, Integer instrumentId, OrderType side,
                          BigDecimal quantity, BigDecimal price, Instant filledAt) {
}
