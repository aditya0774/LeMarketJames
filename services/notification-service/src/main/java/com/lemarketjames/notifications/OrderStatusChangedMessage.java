package com.lemarketjames.notifications;

import java.time.Instant;

/**
 * What this service reads from an {@code OrderStatusChanged} record (contract C6). It is this
 * service's own copy of the shape, so it needs none of buy-sell-service's classes; the statuses
 * stay text, because the list of them belongs to buy-sell-service (contract C1).
 *
 * @param orderId    the order
 * @param accountId  the order's account, whose client is notified
 * @param from       the status before the transition
 * @param to         the status after it
 * @param occurredAt when the transition happened (UTC)
 */
public record OrderStatusChangedMessage(Integer orderId, Integer accountId, String from, String to,
                                        Instant occurredAt) {

    /** @return whether every field a notification needs is present */
    public boolean isComplete() {
        return orderId != null && accountId != null && from != null && to != null && occurredAt != null;
    }
}
