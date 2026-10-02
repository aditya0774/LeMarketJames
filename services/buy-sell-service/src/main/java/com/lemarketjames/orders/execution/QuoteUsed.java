package com.lemarketjames.orders.execution;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The quote an order was priced from at execution. Kept with the fill so a trade can always be
 * traced back to the market data behind its price.
 *
 * @param price     price per share taken from the quote: the ask for a BUY, the bid for a SELL
 * @param source    the feed the quote came from
 * @param quoteTime when the feed produced the quote (UTC)
 */
public record QuoteUsed(BigDecimal price, String source, Instant quoteTime) {
}
