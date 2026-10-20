package com.lemarketjames.market.service;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only access to past prices, for charts. Kept apart from {@link MarketDataService} (current
 * prices) so a caller that only needs quotes does not depend on history, and the other way round.
 */
public interface PriceHistoryService {

    /**
     * @param instrumentId id from {@code instruments}
     * @param from         first trading day wanted, inclusive
     * @return one bar per trading day from {@code from}, oldest first, ending with today's bar so far;
     *         empty if the instrument is unknown or history is unavailable
     */
    List<DailyBar> dailyBars(int instrumentId, LocalDate from);

    /**
     * @param instrumentId id from {@code instruments}
     * @return the current trading day's prices, one per completed minute and then the latest price,
     *         oldest first; empty if the instrument is unknown or history is unavailable
     */
    List<PricePoint> intraday(int instrumentId);
}
