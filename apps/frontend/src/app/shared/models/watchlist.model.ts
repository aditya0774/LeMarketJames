/** One watched stock (C6: Watchlist). Prices come from the quotes the app already polls. */
export interface WatchlistItem {
  symbol: string;
  addedAt: string;
}

/** GET /api/v1/watchlist */
export interface WatchlistResponse {
  success: boolean;
  watchlist: WatchlistItem[];
}
