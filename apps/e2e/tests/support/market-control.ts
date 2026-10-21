import { APIRequestContext, expect } from '@playwright/test';
import { INTERNAL_HEADERS } from './internal-api';

/**
 * Helper to stabilize market prices for deterministic E2E tests.
 * Uses the market control API (C4-quote-feed contract) to pin prices.
 * 
 * Market control endpoint (trading-service): http://localhost:8084/internal/market/control/prices/{ticker}
 */

/**
 * All tickers in the seed data (LMKT-29 holdings test uses seed_active user)
 */
const SEED_HOLDINGS_TICKERS = [
  'AAPL', 'MSFT', 'NVDA', 'GOOGL', 'AMZN', 
  'JPM', 'KO', 'TSLA', 'META', 'AMD', 'COST', 'WMT'
];

// The controls are served by trading-service itself, never through a web app (C4), so they
// need their own address when the stack isn't on this machine.
const MARKET_SERVICE_URL = process.env.E2E_MARKET_URL ?? 'http://localhost:8084';

/** The fields of the market's raw quote that execution tests compare a fill against. */
export interface FeedQuote {
  lastPrice: number;
  bidPrice: number;
  askPrice: number;
  /** ISO-8601 UTC: when the feed produced this quote. */
  lastUpdated: string;
}

/** Feed modes; the values and their meaning are defined in trading-service's FeedMode (C4). */
export type FeedMode = 'LIVE' | 'STALE' | 'UNAVAILABLE';

/**
 * Sets one stock's price and holds it there, so a test knows the exact quote an order executes at.
 * A held price is still a fresh quote (C4). Fails the test if the controls are not enabled, unlike
 * pinMarketPrices below, because a test that depends on the price cannot continue without it.
 */
export async function pinPrice(request: APIRequestContext, ticker: string, price: number): Promise<void> {
  const response = await request.put(`${MARKET_SERVICE_URL}/internal/market/control/prices/${ticker}`, {
    headers: INTERNAL_HEADERS,
    data: { price, pinned: true },
  });
  expect(response.status(), `pin ${ticker}: is sim.control.enabled on? ${await response.text()}`).toBe(200);
}

/** Lets a stock pinned with pinPrice move again. */
export async function releasePrice(request: APIRequestContext, ticker: string): Promise<void> {
  const response = await request.delete(`${MARKET_SERVICE_URL}/internal/market/control/prices/${ticker}`, {
    headers: INTERNAL_HEADERS,
  });
  expect(response.status(), await response.text()).toBe(200);
}

/** The quote the market is serving for a stock right now, as orders-service reads it. */
export async function getQuote(request: APIRequestContext, ticker: string): Promise<FeedQuote> {
  const response = await request.get(`${MARKET_SERVICE_URL}/api/market/quotes/${ticker}`);
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

/**
 * Switches the whole feed. The mode is global, so only tests in the `feed-failure` Playwright
 * project may call this: it runs after every other test has finished (playwright.config.ts).
 */
export async function setFeedMode(request: APIRequestContext, mode: FeedMode): Promise<void> {
  const response = await request.put(`${MARKET_SERVICE_URL}/internal/market/control/feed`, {
    headers: INTERNAL_HEADERS,
    data: { mode },
  });
  expect(response.status(), `feed ${mode}: is sim.control.enabled on? ${await response.text()}`).toBe(200);
}

/** Back to a LIVE feed with nothing pinned. */
export async function resetFeed(request: APIRequestContext): Promise<void> {
  const response = await request.post(`${MARKET_SERVICE_URL}/internal/market/control/reset`, {
    headers: INTERNAL_HEADERS,
  });
  expect(response.status(), await response.text()).toBe(200);
}

/**
 * Pin all market prices to prevent drift during E2E tests.
 * 
 * @param request Playwright APIRequestContext (from test fixture)
 * @param baseUrl trading-service base URL, where the market controls live (defaults to http://localhost:8084)
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
          headers: INTERNAL_HEADERS,
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
 * @param baseUrl trading-service base URL, where the market controls live (defaults to http://localhost:8084)
 */
export async function unpinMarketPrices(request: APIRequestContext, baseUrl = MARKET_SERVICE_URL): Promise<void> {
  for (const ticker of SEED_HOLDINGS_TICKERS) {
    try {
      const response = await request.delete(`${baseUrl}/internal/market/control/prices/${ticker}`, {
        headers: INTERNAL_HEADERS,
      });
      if (!response.ok()) {
        console.warn(`Failed to unpin price for ${ticker}: ${response.status()}`);
      }
    } catch (err) {
      console.warn(`Failed to unpin ${ticker}:`, err);
    }
  }
}
