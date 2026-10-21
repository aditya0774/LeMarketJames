package com.lemarketjames.orders.execution;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The quote an order was priced from at execution, or refused on. Kept with the fill, or in the
 * rejection's audit event, so an outcome can always be traced back to the market data behind it.
 *
 * @param price     price per share taken from the quote: the ask for a BUY, the bid for a SELL;
 *                  null only on a refused quote whose price wasn't a number
 * @param source    the feed the quote came from
 * @param quoteTime when the feed produced the quote (UTC)
 */
public record QuoteUsed(BigDecimal price, String source, Instant quoteTime) {
}
