package com.lemarketjames.market.service;

import java.util.Set;

/**
 * Steering for the simulated feed (contract C4). Kept separate from MarketDataService so quote
 * readers never see the controls.
 */
public interface MarketFeedControl {

    FeedMode feedMode();

    void setFeedMode(FeedMode mode);

    /**
     * Sets a stock's price.
     *
     * @param ticker symbol, case-insensitive
     * @param price  new price, above zero
     * @param pinned true to hold the price there until released; false to let it move from there
     * @throws IllegalArgumentException if the ticker isn't simulated or the price isn't positive
     */
    void setPrice(String ticker, double price, boolean pinned);

    /** Lets a pinned stock move again. @throws IllegalArgumentException if the ticker isn't simulated */
    void releasePrice(String ticker);

    /** Tickers whose price is currently pinned. */
    Set<String> pinnedTickers();

    /** Back to normal: LIVE feed and no pinned prices. Prices already set stay where they are. */
    void reset();
}
