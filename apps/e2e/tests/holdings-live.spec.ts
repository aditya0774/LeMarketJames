import { APIRequestContext, Page, expect, test } from '@playwright/test';
import { instrumentIdOf, loginViaApi, placeOrder, waitForFinalStatus } from './support/orders';
import { loginViaUi, newUser, registerViaApi } from './support/users';

/**
 * LMKT-28: holdings and cash on the Overview follow a settlement live, through the portfolio
 * stream (GET /api/v1/portfolio/stream), and catch up after the connection drops.
 *
 * The order-status stream is blocked in both tests: the dashboard refreshes on its events too,
 * so leaving it open would let these pass without the portfolio stream. DevTools "offline" does
 * not drop an open event stream, so a dropped connection is an aborted route instead.
 * Run with SIM_RESPECT_MARKET_HOURS=false, like execution.spec.ts.
 */

// Bought here only, so no other suite's assertions depend on it.
const SYMBOL = 'NVDA';
const ORDER_STREAM = '**/api/v1/orders/stream';
const PORTFOLIO_STREAM = '**/api/v1/portfolio/stream';

const buyingPower = (page: Page) => page.locator('.stat-card').filter({ hasText: 'Buying power' }).locator('.val');
const ownedSymbol = (page: Page) => page.locator('app-holdings-split .sym', { hasText: SYMBOL });

const usd = (amount: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);

/** Buys one share through the API, waits for the fill and returns the buying power it left. */
async function buyOneAndWaitForFill(request: APIRequestContext, accountId: number): Promise<string> {
  const order = await placeOrder(request, accountId, await instrumentIdOf(request, SYMBOL), 'BUY');
  expect((await waitForFinalStatus(request, order.orderId)).orderStatus).toBe('FILLED');
  const balance = (await (await request.get('/api/v1/portfolio')).json()).balance;
  return usd(balance.buyingPower);
}

test('LMKT-28 AC1: a settled fill updates holdings and buying power within 2 seconds', async ({ page, request }) => {
  const user = newUser('livehold');
  await registerViaApi(request, user);
  await page.route(ORDER_STREAM, (route) => route.abort());

  // Headers arrive only once the server has registered the stream, so a fill after this is seen.
  const streamOpen = page.waitForResponse((response) => response.url().endsWith('/api/v1/portfolio/stream'));
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);
  expect((await streamOpen).status()).toBe(200);
  await expect(buyingPower(page)).toHaveText('$10,000.00');
  await expect(ownedSymbol(page)).toHaveCount(0);

  const accountId = await loginViaApi(request, user);
  const expected = await buyOneAndWaitForFill(request, accountId);

  await expect(buyingPower(page)).toHaveText(expected, { timeout: 2000 });
  await expect(ownedSymbol(page)).toBeVisible({ timeout: 2000 });
});

test('LMKT-28 AC2: after the connection drops, the latest holdings and buying power show on reconnect', async ({ page, request }) => {
  const user = newUser('livehold');
  await registerViaApi(request, user);
  await page.route(ORDER_STREAM, (route) => route.abort());
  await page.route(PORTFOLIO_STREAM, (route) => route.abort());

  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(buyingPower(page)).toHaveText('$10,000.00');

  const accountId = await loginViaApi(request, user);
  const expected = await buyOneAndWaitForFill(request, accountId);

  // Disconnected, the page cannot know about the fill.
  await page.waitForTimeout(1000);
  await expect(buyingPower(page)).toHaveText('$10,000.00');
  await expect(ownedSymbol(page)).toHaveCount(0);

  // The browser retries the stream on its own; once it gets through, the page catches up.
  await page.unroute(PORTFOLIO_STREAM);
  await expect(buyingPower(page)).toHaveText(expected, { timeout: 10_000 });
  await expect(ownedSymbol(page)).toBeVisible({ timeout: 10_000 });
});
