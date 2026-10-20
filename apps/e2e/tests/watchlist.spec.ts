import { expect, test, type Page } from '@playwright/test';
import { openTab } from './support/nav';
import { loginViaUi, logoutViaUi, newUser, registerViaApi } from './support/users';

function watchlistPanel(page: Page) {
  return page.getByRole('complementary', { name: 'Watchlist' });
}

/** The symbols on the Watchlist panel, top to bottom (newest first). */
async function panelSymbols(page: Page): Promise<string[]> {
  return watchlistPanel(page).locator('li .sym').allTextContents();
}

/**
 * LMKT-132 (S41, LMKT-49 AC1-AC3): the Market tab's stars and Watchlist panel against the real
 * trading-service watchlist, persisted across sign-out and sign-in.
 */
test('watched stocks show with a live price, can be removed, and survive a new session', async ({ page, request }) => {
  const user = newUser('watch');
  await registerViaApi(request, user);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);

  await openTab(page, 'Market');
  const panel = watchlistPanel(page);
  // The stars live on the stock table; the panel has Remove buttons with the same names.
  const stocks = page.getByRole('table', { name: 'Live stock prices' });
  await expect(panel).toContainText('Nothing on your watchlist');

  // AC1: added stocks appear in the list, newest first, each with its current indicative price.
  await stocks.getByRole('button', { name: 'Add AAPL to watchlist', exact: true }).click();
  await stocks.getByRole('button', { name: 'Add MSFT to watchlist', exact: true }).click();
  await expect.poll(() => panelSymbols(page)).toEqual(['MSFT', 'AAPL']);
  for (const item of await panel.locator('li').all()) {
    await expect(item.locator('.wl-price .mono').first()).toHaveText(/^\$[\d,]+\.\d{2}$/);
  }
  await expect(stocks.getByRole('button', { name: 'Remove AAPL from watchlist', exact: true }))
    .toHaveAttribute('aria-pressed', 'true');

  // The stars were saved, not just drawn: a reload reads them back from the server.
  await page.reload();
  await expect.poll(() => panelSymbols(page)).toEqual(['MSFT', 'AAPL']);

  // AC2: a removed stock disappears from the list and its star turns off.
  await panel.getByRole('button', { name: 'Remove AAPL from watchlist' }).click();
  await expect.poll(() => panelSymbols(page)).toEqual(['MSFT']);
  await expect(stocks.getByRole('button', { name: 'Add AAPL to watchlist', exact: true }))
    .toHaveAttribute('aria-pressed', 'false');

  // AC3: signing out and back in leaves the watchlist as it was.
  await logoutViaUi(page);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);
  await openTab(page, 'Market');
  await expect.poll(() => panelSymbols(page)).toEqual(['MSFT']);
});

/** The C6 responses the panel relies on, through the trading app's own proxy. */
test('the watchlist API answers as contract C6 describes', async ({ page, request }) => {
  const user = newUser('watchapi');
  await registerViaApi(request, user);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);

  const empty = await page.request.get('/api/v1/watchlist');
  expect(empty.status(), await empty.text()).toBe(200);
  expect(await empty.json()).toEqual({ success: true, watchlist: [] });

  const added = await page.request.post('/api/v1/watchlist', { data: { symbol: 'NVDA' } });
  expect(added.status(), await added.text()).toBe(201);
  expect(await added.json()).toEqual({ success: true, item: { symbol: 'NVDA', addedAt: expect.any(String) } });

  const duplicate = await page.request.post('/api/v1/watchlist', { data: { symbol: 'NVDA' } });
  expect(duplicate.status()).toBe(409);
  expect(await duplicate.json()).toMatchObject({ success: false, code: 'ALREADY_WATCHED' });

  const unknown = await page.request.post('/api/v1/watchlist', { data: { symbol: 'NOPE' } });
  expect(unknown.status()).toBe(404);
  expect(await unknown.json()).toEqual({ success: false, error: 'Symbol not found' });

  const listed = await page.request.get('/api/v1/watchlist');
  expect((await listed.json()).watchlist).toEqual([{ symbol: 'NVDA', addedAt: expect.any(String) }]);

  // Removing twice is harmless: both answer 204 and the list ends empty.
  for (let attempt = 0; attempt < 2; attempt++) {
    const removed = await page.request.delete('/api/v1/watchlist/NVDA');
    expect(removed.status()).toBe(204);
  }
  expect((await (await page.request.get('/api/v1/watchlist')).json()).watchlist).toEqual([]);
});
