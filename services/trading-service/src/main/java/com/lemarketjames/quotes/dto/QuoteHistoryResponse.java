package com.lemarketjames.quotes.dto;

import java.util.List;

/**
 * Successful contract-aligned response for GET /api/quotes/{symbol}/history.
 *
 * @param success always true; errors use {@link QuoteErrorResponse}
 * @param symbol  the stock, upper-cased
 * @param range   the requested range code, e.g. "1M"
 * @param points  the prices over the range, oldest first
 */
public record QuoteHistoryResponse(boolean success, String symbol, String range, List<ChartPointDto> points) {
}
