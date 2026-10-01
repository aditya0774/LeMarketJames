package com.lemarketjames.market.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Tracks whether {@link MarketDataClient}'s calls to market-service are currently succeeding,
 * across every call this service makes. {@code MarketDataClient} reports each call's outcome
 * here; callers who need to distinguish "the feed is down" from an ordinary empty result (an
 * unsimulated instrument) read {@link #isAvailable()}.
 *
 * <p>There is no separate "resume" action: the feed is back up the moment a call succeeds again,
 * which is exactly what {@link #recordSuccess()} observes. Outage start and end are logged here
 * (not persisted — a feed-wide outage has no order, account or client to attach an audit row to).
 */
@Component
public class MarketFeedStatus {

    private static final Logger log = LoggerFactory.getLogger(MarketFeedStatus.class);

    private volatile boolean available = true;
    private volatile Instant outageStartedAt;

    public synchronized void recordFailure() {
        if (available) {
            available = false;
            outageStartedAt = Instant.now();
            log.warn("Quote feed outage started at {}", outageStartedAt);
        }
    }

    public synchronized void recordSuccess() {
        if (!available) {
            Instant resumedAt = Instant.now();
            Duration outageDuration = Duration.between(outageStartedAt, resumedAt);
            log.info("Quote feed outage resolved at {} after {}", resumedAt, outageDuration);
            available = true;
            outageStartedAt = null;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public Optional<Instant> outageStartedAt() {
        return Optional.ofNullable(outageStartedAt);
    }
}
