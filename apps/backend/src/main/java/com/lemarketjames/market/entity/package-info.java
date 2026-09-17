/**
 * JPA persistence layer for the market feature: simulation parameters per instrument, the latest
 * price snapshot per instrument, and 1-minute OHLC candles
 * (database/schema/006_market_simulation.sql).
 *
 * <p>These entities link to {@code instruments} by plain id rather than a JPA relationship, so the
 * market feature stays independent of the orders feature's {@code Instrument} entity (see the
 * feature dependency rules in AGENTS.md).
 */
package com.lemarketjames.market.entity;
