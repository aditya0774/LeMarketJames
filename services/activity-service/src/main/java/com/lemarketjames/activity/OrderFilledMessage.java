package com.lemarketjames.activity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What this service reads from an {@code OrderFilled} record (contract C6). It is this service's
 * own copy of the shape, so it needs none of buy-sell-service's classes. The record also carries
 * the order's account, which is deliberately not read: activity never says who traded.
 *
 * @param orderId      the order that filled
 * @param instrumentId the stock traded
 * @param side         BUY or SELL, as text
 * @param quantity     shares filled
 * @param price        price per share
 * @param filledAt     when the fill happened (UTC)
 */
public record OrderFilledMessage(Integer orderId, Integer instrumentId, String side, BigDecimal quantity,
                                 BigDecimal price, Instant filledAt) {

    /** @return whether every field a recorded fill needs is present */
    public boolean isComplete() {
        return orderId != null && instrumentId != null && side != null && quantity != null && price != null
            && filledAt != null;
    }
}
