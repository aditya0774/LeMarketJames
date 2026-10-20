package com.lemarketjames.holdings.stream;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A settlement changed an account's cash and holdings. Sent to that account's open portfolio
 * streams once the settlement has committed, so the client re-reads holdings and balance.
 *
 * @param accountId  the account that was settled
 * @param orderId    the order whose fill was settled
 * @param cash       the account's cash after the settlement
 * @param occurredAt when the settlement was applied (UTC)
 */
public record BalanceChanged(Integer accountId, Integer orderId, BigDecimal cash, Instant occurredAt) {
}
