import { APIRequestContext } from '@playwright/test';

/**
 * Helper to stabilize market prices for deterministic E2E tests.
 * Uses the market control API (C4-quote-feed contract) to pin prices.
 * 
 * Market service control endpoint: http://localhost:8083/internal/market/control/prices/{ticker}
 */

/**
 * All tickers in the seed data (LMKT-29 holdings test uses seed_active user)
 */
const SEED_HOLDINGS_TICKERS = [
  'AAPL', 'MSFT', 'NVDA', 'GOOGL', 'AMZN', 
  'JPM', 'KO', 'TSLA', 'META', 'AMD', 'COST', 'WMT'
];

const MARKET_SERVICE_URL = 'http://localhost:8083';

/**
 * Pin all market prices to prevent drift during E2E tests.
 * 
 * @param request Playwright APIRequestContext (from test fixture)
 * @param baseUrl Market service base URL (defaults to http://localhost:8083)
 * 
 * Usage:
 *   await pinMarketPrices(request);
 * 
 * This ensures that when holdings are fetched, prices remain stable for multi-step test scenarios
 * where page reloads or navigations occur (e.g., AC3: Holdings persist after page reload).
 */
export async function pinMarketPrices(request: APIRequestContext, baseUrl = MARKET_SERVICE_URL): Promise<void> {
  // Fetch current prices for all seed holdings
  const prices: Array<{ ticker: string; price: number | null }> = [];

  for (const ticker of SEED_HOLDINGS_TICKERS) {
    try {
      const response = await request.get(`${baseUrl}/api/market/quotes/${ticker}`);
      if (response.ok()) {
        const data = await response.json();
        prices.push({ ticker, price: data.lastPrice });
      } else {
        console.warn(`Failed to fetch price for ${ticker}: ${response.status()}`);
        prices.push({ ticker, price: null });
      }
    } catch (err) {
      console.warn(`Failed to fetch price for ${ticker}:`, err);
      prices.push({ ticker, price: null });
    }
  }

  // Pin each price via the control API
  for (const { ticker, price } of prices) {
    if (price !== null) {
      try {
        const response = await request.put(`${baseUrl}/internal/market/control/prices/${ticker}`, {
          data: { price, pinned: true }
        });
        if (!response.ok()) {
          console.warn(`Failed to pin price for ${ticker}: ${response.status()}`);
        }
      } catch (err) {
        console.warn(`Failed to pin ${ticker}:`, err);
      }
    }
  }
}

/**
 * Unpin all market prices to resume normal market simulation.
 * 
 * @param request Playwright APIRequestContext (from test fixture)
 * @param baseUrl Market service base URL (defaults to http://localhost:8083)
 */
export async function unpinMarketPrices(request: APIRequestContext, baseUrl = MARKET_SERVICE_URL): Promise<void> {
  for (const ticker of SEED_HOLDINGS_TICKERS) {
    try {
      const response = await request.delete(`${baseUrl}/internal/market/control/prices/${ticker}`);
      if (!response.ok()) {
        console.warn(`Failed to unpin price for ${ticker}: ${response.status()}`);
      }
    } catch (err) {
      console.warn(`Failed to unpin ${ticker}:`, err);
    }
  }
}
