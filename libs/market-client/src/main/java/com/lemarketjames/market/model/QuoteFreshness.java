package com.lemarketjames.market.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether a quote is too old to price an order with (contract C4/C5). The limit is the
 * platform setting {@code lmj.market.staleness-limit}; callers pass it in so this class stays free
 * of configuration.
 */
public final class QuoteFreshness {

    private QuoteFreshness() {
    }

    /**
     * @param quote the quote to check
     * @param limit the oldest a quote may be and still be used
     * @param clock the current time
     * @return true when the quote is older than {@code limit}, or has no timestamp at all
     */
    public static boolean isStale(QuoteSnapshot quote, Duration limit, Clock clock) {
        Instant updated = quote.lastUpdated();
        return updated == null || Duration.between(updated, clock.instant()).compareTo(limit) > 0;
    }
}
