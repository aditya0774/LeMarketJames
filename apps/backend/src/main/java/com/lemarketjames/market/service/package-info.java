/**
 * The running market: the simulator that advances prices, the scheduler that drives it, database
 * loading/saving, and the read-only {@link com.lemarketjames.market.service.MarketDataService}
 * other features depend on.
 *
 * <p>Threading: the simulator's {@code tick} and {@code load} are synchronized and are the only
 * writers of price state; readers get immutable snapshots from concurrent maps and never block.
 * Spring's default single-threaded scheduler means a tick and a save never overlap.
 */
package com.lemarketjames.market.service;
