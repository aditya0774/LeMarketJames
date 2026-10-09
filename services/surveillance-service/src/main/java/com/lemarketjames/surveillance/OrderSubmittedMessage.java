package com.lemarketjames.surveillance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What this service reads from an {@code OrderSubmitted} record (contract C6). It is this
 * service's own copy of the shape, so it needs none of buy-sell-service's classes.
 *
 * @param orderId      the order
 * @param accountId    the order's account
 * @param instrumentId the stock ordered
 * @param side         BUY or SELL, as text
 * @param quantity     shares ordered
 * @param price        price per share the order was placed at; null for a SELL
 * @param submittedAt  when the order was placed (UTC)
 */
public record OrderSubmittedMessage(Integer orderId, Integer accountId, Integer instrumentId, String side,
                                    BigDecimal quantity, BigDecimal price, Instant submittedAt) {

    /** @return whether every field an alert needs is present; the price is optional */
    public boolean isComplete() {
        return orderId != null && accountId != null && instrumentId != null && side != null
            && quantity != null && submittedAt != null;
    }
}
