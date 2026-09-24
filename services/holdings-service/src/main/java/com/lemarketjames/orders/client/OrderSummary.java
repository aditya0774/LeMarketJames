package com.lemarketjames.orders.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The subset of core-service's {@code OrderResponse} this service needs for trade history and
 * buying power. Deliberately its own type rather than sharing core-service's class across the wire
 * (each service owns its own view of the JSON contract) — unknown fields are ignored by Jackson's
 * default lenient config.
 */
public record OrderSummary(
        Integer instrumentId,
        String orderType,
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        String orderStatus,
        LocalDateTime filledAt) {

    public boolean isFilled() {
        return "FILLED".equals(orderStatus);
    }

    public boolean isOpen() {
        return switch (orderStatus) {
            case "SUBMITTED", "ACCEPTED", "PENDING", "DELAYED" -> true;
            default -> false;
        };
    }

    public boolean isBuy() {
        return "BUY".equals(orderType);
    }
}
