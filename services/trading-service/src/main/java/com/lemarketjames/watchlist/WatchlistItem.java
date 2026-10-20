package com.lemarketjames.watchlist;

import java.time.Instant;

/** One watched stock as contract C6 returns it. Prices are not part of it: the app polls quotes. */
public record WatchlistItem(String symbol, Instant addedAt) {
}
