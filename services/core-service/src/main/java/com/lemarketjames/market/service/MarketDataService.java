package com.lemarketjames.market.service;

import com.lemarketjames.market.model.QuoteSnapshot;

import java.util.Collection;
import java.util.Optional;

/**
 * Read-only access to current simulated market prices.
 *
 * <p>This is the single entry point other features (quotes, holdings, and later orders) should
 * use for prices, so they never depend on how prices are produced.
 */
public interface MarketDataService {

    /**
     * @param ticker symbol, case-insensitive
     * @return the latest quote, or empty if the symbol is not simulated
     */
    Optional<QuoteSnapshot> findByTicker(String ticker);

    /**
     * @param instrumentId id from {@code instruments}
     * @return the latest quote, or empty if the instrument is not simulated
     */
    Optional<QuoteSnapshot> findByInstrumentId(int instrumentId);

    /** @return latest quotes for all simulated instruments */
    Collection<QuoteSnapshot> findAll();
}
