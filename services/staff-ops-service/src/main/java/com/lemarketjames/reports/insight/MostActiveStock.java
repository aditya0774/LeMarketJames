package com.lemarketjames.reports.insight;

import java.math.BigDecimal;

/**
 * One stock's aggregate activity for the current reports week so far.
 *
 * @param previousTradedValue the stock's traded value over the same days of the week before; 0 when it did not trade
 * @param flag                whether {@code tradedValue} moved enough against it to be flagged (BR-17)
 */
public record MostActiveStock(int rank, String symbol, BigDecimal tradedValue,
                              long tradeCount, BigDecimal shareVolume,
                              BigDecimal previousTradedValue, InsightFlag flag) {
}
