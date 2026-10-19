package com.lemarketjames.holdings.client;

import java.math.BigDecimal;

/** Body posted to trading-service's {@code POST /internal/holdings/validate}. */
public record HoldingsValidationRequest(
        Integer accountId,
        String username,
        Integer instrumentId,
        BigDecimal sellQuantity) {
}
