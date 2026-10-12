package com.lemarketjames.surveillance;

import java.time.Instant;

/**
 * What this service reads from an {@code OrderStatusChanged} record (contract C6). It is this
 * service's own copy of the shape, so it needs none of buy-sell-service's classes; the status and
 * the rejection reason stay text, because their lists belong to buy-sell-service (contract C1).
 *
 * @param orderId         the order
 * @param accountId       the order's account
 * @param to              the status the order moved to
 * @param rejectionReason why it was rejected; null for any other move, and in a record published
 *                        before the event carried a reason
 * @param occurredAt      when the order changed status (UTC)
 */
public record OrderStatusChangedMessage(Integer orderId, Integer accountId, String to, String rejectionReason,
                                        Instant occurredAt) {

    /** @return whether every field an alert needs is present; the rejection reason is optional */
    public boolean isComplete() {
        return orderId != null && accountId != null && to != null && occurredAt != null;
    }
}
