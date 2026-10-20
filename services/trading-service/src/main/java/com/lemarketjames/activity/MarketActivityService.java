package com.lemarketjames.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Records fills and sums them into market activity.
 *
 * <p>Kafka can deliver a record more than once, after a restart or a failed commit. Recording is
 * therefore idempotent: a fill is stored under its order id and an order fills once, so a fill
 * that is already stored is left alone and no trade is ever counted twice.
 */
@Service
public class MarketActivityService {
    private static final Logger log = LoggerFactory.getLogger(MarketActivityService.class);

    /**
     * How far back market activity looks. A rolling day rather than a calendar day, so the
     * figures need no time zone and never drop to zero at midnight.
     */
    public static final Duration WINDOW = Duration.ofHours(24);

    private final RecordedFillRepository fills;
    private final Clock clock;

    public MarketActivityService(RecordedFillRepository fills, Clock clock) {
        this.fills = fills;
        this.clock = clock;
    }

    /**
     * Stores one fill.
     *
     * @param fill the fill, as read from Kafka
     * @return whether it was stored; false for a fill that was already recorded or an event that
     *         lacks a field the record needs
     */
    @Transactional
    public boolean record(OrderFilledMessage fill) {
        if (!fill.isComplete()) {
            log.warn("Kafka: skipped an incomplete fill: {}", fill);
            return false;
        }
        if (fills.existsById(fill.orderId())) {
            log.info("Kafka: the fill of order {} was already recorded", fill.orderId());
            return false;
        }
        fills.save(new RecordedFill(fill));
        log.info("Fill recorded: order={} instrument={} quantity={} price={}", fill.orderId(), fill.instrumentId(),
            fill.quantity(), fill.price());
        return true;
    }

    /** @return when the activity window that ends now began */
    public Instant windowStart() {
        return clock.instant().minus(WINDOW);
    }

    /**
     * @param since the start of the window, included
     * @return the activity of every stock that traded from then on, in instrument id order
     */
    @Transactional(readOnly = true)
    public List<InstrumentActivity> activitySince(Instant since) {
        return fills.summarizeSince(since);
    }
}
