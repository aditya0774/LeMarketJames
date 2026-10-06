package com.lemarketjames.orders.submission;

import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.RejectionReason;
import java.math.BigDecimal;

/**
 * What the submission checks decided: the order may be saved (at {@code price}, null for a SELL),
 * or it is refused with the response the caller gets.
 */
public record ValidationOutcome(BigDecimal price, OrderResponse refusal) {

    public static ValidationOutcome passed(BigDecimal price) {
        return new ValidationOutcome(price, null);
    }

    public static ValidationOutcome refused(String message, RejectionReason reason) {
        return new ValidationOutcome(null, new OrderResponse(false, message, reason.name()));
    }

    public boolean isRefused() {
        return refusal != null;
    }
}
