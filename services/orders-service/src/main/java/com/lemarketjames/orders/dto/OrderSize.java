package com.lemarketjames.orders.dto;

import java.math.BigDecimal;

/**
 * How an order request says how much to trade (BR-04): a number of shares or a dollar amount,
 * never both and never neither. Shared by both order requests so the rule and its message are
 * written once.
 */
final class OrderSize {

    static final String EXACTLY_ONE = "Give either quantity or amount, not both";

    private OrderSize() {
    }

    static boolean exactlyOne(BigDecimal quantity, BigDecimal amount) {
        return (quantity == null) != (amount == null);
    }
}
