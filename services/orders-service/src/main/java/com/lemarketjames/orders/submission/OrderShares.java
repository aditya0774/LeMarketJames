package com.lemarketjames.orders.submission;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The share arithmetic of the QUANTITY rule (BR-04): orders are for whole shares, and an order by
 * amount buys or sells as many whole shares as the amount covers. Kept apart from the validator so
 * the rounding is written, and tested, in one place.
 */
final class OrderShares {

    private OrderShares() {
    }

    /** As many whole shares as {@code amount} covers at {@code price}; zero when it doesn't cover one. */
    static BigDecimal forAmount(BigDecimal amount, BigDecimal price) {
        // Always round down: rounding up would spend more than the client asked for.
        return amount.divide(price, 0, RoundingMode.DOWN);
    }

    /** Whether {@code quantity} is a whole number of shares, at least one. */
    static boolean isWholeShares(BigDecimal quantity) {
        // stripTrailingZeros, so 2.0000 (the database scale) counts as the whole number it is.
        return quantity != null && quantity.signum() > 0 && quantity.stripTrailingZeros().scale() <= 0;
    }
}
