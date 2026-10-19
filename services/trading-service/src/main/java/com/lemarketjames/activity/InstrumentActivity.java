package com.lemarketjames.activity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * How much of one stock traded in the activity window (contract C6). It is a sum over fills and
 * names no order and no account.
 *
 * @param instrumentId the stock
 * @param trades       how many fills
 * @param volume       shares traded, buys and sells together
 * @param turnover     their value: the sum of quantity times price, in USD
 * @param lastTradedAt when the latest of those fills happened (UTC)
 */
public record InstrumentActivity(Integer instrumentId, long trades, BigDecimal volume, BigDecimal turnover,
                                 Instant lastTradedAt) {
}
