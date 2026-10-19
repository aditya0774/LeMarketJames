package com.lemarketjames.market.service;

/**
 * How the simulated quote feed behaves (contract C4), so failure handling can be tested on demand.
 */
public enum FeedMode {
    /** Normal: prices move and quotes are current. */
    LIVE,
    /**
     * Quotes are frozen and claim to be old ({@code sim.control.stale-age} in the past), so any
     * staleness check fails immediately. Prices keep moving underneath and reappear on LIVE.
     */
    STALE,
    /** Every quote endpoint answers 503 Service Unavailable, as if the feed were down. */
    UNAVAILABLE
}
