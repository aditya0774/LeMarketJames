/**
 * Simulated stock market: the platform's source of current prices (BR-08, BR-12, BR-13).
 *
 * <p>Prices follow Geometric Brownian Motion (GBM) and advance on a timer, never as a side effect
 * of a quote being read, so an order can always be priced against a current, non-stale quote.
 * Live prices live in memory; the latest price per instrument and 1-minute OHLC candles are
 * written to the database periodically, so a restart resumes the market where it left off
 * (service expectation 9.1).
 *
 * <p>Other features read prices through
 * {@link com.lemarketjames.market.service.MarketDataService} only, which keeps this feature
 * replaceable by a real market data feed.
 *
 * <p>Per-instrument settings (drift, volatility, spread, ...) are data, not code: see
 * {@code instrument_market_params} in database/schema/006_market_simulation.sql. Runtime settings
 * are the {@code sim.*} properties in application.properties.
 *
 * <p>Design notes, tuning and planned extensions: {@code docs/MARKET.md}.
 */
package com.lemarketjames.market;
