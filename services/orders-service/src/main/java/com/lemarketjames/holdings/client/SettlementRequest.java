package com.lemarketjames.holdings.client;

import com.lemarketjames.orders.entity.Order;

import java.math.BigDecimal;

/** Body posted to trading-service's {@code POST /internal/holdings/settle}. */
public record SettlementRequest(
        Integer orderId,
        Integer accountId,
        Integer instrumentId,
        Order.OrderType orderType,
        BigDecimal quantity,
        BigDecimal pricePerUnit) {
}
