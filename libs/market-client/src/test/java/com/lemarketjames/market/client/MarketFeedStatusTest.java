package com.lemarketjames.market.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketFeedStatusTest {

    @Test
    void startsAvailableWithNoOutage() {
        MarketFeedStatus status = new MarketFeedStatus();

        assertTrue(status.isAvailable());
        assertTrue(status.outageStartedAt().isEmpty());
    }

    @Test
    void recordFailureStartsAnOutage() {
        MarketFeedStatus status = new MarketFeedStatus();

        status.recordFailure();

        assertFalse(status.isAvailable());
        assertTrue(status.outageStartedAt().isPresent());
    }

    @Test
    void repeatedFailuresDoNotMoveTheOutageStart() {
        MarketFeedStatus status = new MarketFeedStatus();

        status.recordFailure();
        Instant firstStart = status.outageStartedAt().orElseThrow();
        status.recordFailure();
        status.recordFailure();

        assertEquals(firstStart, status.outageStartedAt().orElseThrow());
    }

    @Test
    void recordSuccessAfterAFailureResumesTheFeed() {
        MarketFeedStatus status = new MarketFeedStatus();

        status.recordFailure();
        status.recordSuccess();

        assertTrue(status.isAvailable());
        assertTrue(status.outageStartedAt().isEmpty());
    }

    @Test
    void recordSuccessWhileAlreadyAvailableIsANoOp() {
        MarketFeedStatus status = new MarketFeedStatus();

        status.recordSuccess();

        assertTrue(status.isAvailable());
        assertTrue(status.outageStartedAt().isEmpty());
    }
}
