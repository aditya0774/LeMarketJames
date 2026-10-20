package com.lemarketjames.reports.insight;

import java.math.BigDecimal;

/** One stock's aggregate activity for the current reports week. */
public record MostActiveStock(int rank, String symbol, BigDecimal tradedValue,
                              long tradeCount, BigDecimal shareVolume) {
}