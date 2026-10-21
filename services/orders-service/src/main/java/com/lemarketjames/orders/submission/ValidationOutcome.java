package com.lemarketjames.orders.submission;

import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.RejectionReason;
import java.math.BigDecimal;

/**
 * What the submission checks decided: the order may be saved for {@code quantity} whole shares (as
 * asked for, or worked out from an amount, BR-04) at {@code price} (null for a SELL), or it is
 * refused with the response the caller gets.
 */
public record ValidationOutcome(BigDecimal price, BigDecimal quantity, OrderResponse refusal) {

    public static ValidationOutcome passed(BigDecimal price, BigDecimal quantity) {
        return new ValidationOutcome(price, quantity, null);
    }

    public static ValidationOutcome refused(String message, RejectionReason reason) {
        return new ValidationOutcome(null, null, new OrderResponse(false, message, reason.name()));
    }

    public boolean isRefused() {
        return refusal != null;
    }
}
