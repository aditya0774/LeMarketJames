package com.lemarketjames.cash;

import java.math.BigDecimal;

/** The durable cash outcome and whether this call applied it or replayed it. */
public record CashMovementResult(BigDecimal cashBalanceAfter, boolean replayed) {
}
