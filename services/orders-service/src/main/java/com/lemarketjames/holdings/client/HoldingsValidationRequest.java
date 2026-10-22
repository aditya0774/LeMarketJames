package com.lemarketjames.holdings.client;

import java.math.BigDecimal;

/**
 * Body posted to trading-service's {@code POST /internal/holdings/validate}. {@code reservedQuantity}
 * is what the account's open SELLs of the stock already promise (BR-05), so trading-service checks
 * the sale against the holding less that.
 */
public record HoldingsValidationRequest(
        Integer accountId,
        String username,
        Integer instrumentId,
        BigDecimal sellQuantity,
        BigDecimal reservedQuantity) {
}
