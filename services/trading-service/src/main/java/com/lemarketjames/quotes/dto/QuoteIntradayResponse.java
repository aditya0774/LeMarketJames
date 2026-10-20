package com.lemarketjames.quotes.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Successful contract-aligned response for GET /api/quotes/intraday: every stock's price over the
 * current trading day in one call, for the trading app's small trend graphs.
 *
 * @param success always true; errors use {@link QuoteErrorResponse}
 * @param points  per symbol, the day's minute closes oldest first, ending with the live price
 */
public record QuoteIntradayResponse(boolean success, Map<String, List<BigDecimal>> points) {
}
