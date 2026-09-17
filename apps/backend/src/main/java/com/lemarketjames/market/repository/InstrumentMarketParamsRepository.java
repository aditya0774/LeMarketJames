package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.InstrumentMarketParamsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstrumentMarketParamsRepository extends JpaRepository<InstrumentMarketParamsEntity, Integer> {

    /**
     * Loads every simulated instrument: identity columns from {@code instruments} joined with
     * its simulation settings.
     *
     * <p>A native query is used so the market feature does not import the orders feature's
     * {@code Instrument} entity (keeping feature dependencies acyclic, see AGENTS.md). Columns are
     * returned positionally; see {@code MarketPersistenceService#toMarketInstrument} for the mapping.
     * Instruments without a params row are not simulated and therefore have no quote.
     */
    @Query(value = """
            SELECT i.instrument_id, i.ticker, i.name, i.asset_class, i.currency, i.location,
                   p.initial_price, p.drift, p.volatility, p.market_correlation, p.spread_bps,
                   p.shares_outstanding, p.avg_daily_volume, p.earnings_per_share, p.dividend_per_share
            FROM instruments i
            JOIN instrument_market_params p ON p.instrument_id = i.instrument_id
            ORDER BY i.instrument_id
            """, nativeQuery = true)
    List<Object[]> findAllSimulatedInstruments();
}
