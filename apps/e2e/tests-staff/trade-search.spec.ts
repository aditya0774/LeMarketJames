import { APIRequestContext, test as base, expect, Page } from '@playwright/test';
import { connectDatabase } from '../tests/support/database';
import { loginViaApi } from '../tests/support/orders';
import { newUser, registerViaApi, TestUser } from '../tests/support/users';
import { signIn, TRADING_OPS } from './support/staff';

/**
 * LMKT-39 trade search, on the staff app since LMKT-145 moved it out of the trading app.
 * Sign-in, searches and authorization all use the real staff app, staff gateway and backend,
 * without mocks.
 */
const SEARCH = '/api/v1/orders/trades/search';
// Clients register through the trading app; the staff gateway has no registration route.
const TRADING_APP = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

interface SearchFixture {
  user: TestUser;
  clientId: number;
  accountId: number;
  firstId: number;
  lastId: number;
}

// Historical fixtures follow order-history.spec.ts: only data setup uses SQL.
// Final statuses keep the execution worker from changing these read-side fixtures.
const test = base.extend<{ tradingApp: APIRequestContext; trades: SearchFixture }>({
  tradingApp: async ({ playwright }, use) => {
    const context = await playwright.request.newContext({ baseURL: TRADING_APP });
    await use(context);
    await context.dispose();
  },
  trades: async ({ tradingApp }, use) => {
    const db = await connectDatabase();
    const users = [newUser('search'), newUser('other')];
    try {
      for (const user of users) await registerViaApi(tradingApp, user);
      const accounts = await Promise.all(users.map(async user => {
        const result = await db.query(
          'SELECT a.account_id, a.client_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1',
          [user.username]);
        expect(result.rows).toHaveLength(1);
        return result.rows[0] as { account_id: number; client_id: number };
      }));
      const insert = async (accountId: number, at: string, status = 'FILLED', side = 'BUY') => {
        const result = await db.query(
          "INSERT INTO orders (account_id,instrument_id,order_type,quantity,price_per_unit,order_status,submitted_at,filled_at,created_at,updated_at) " +
          "SELECT $1,instrument_id,$2,2,123.4567,$3,'2025-12-01'::timestamp,$4::timestamp,'2025-12-01'::timestamp,'2025-12-01'::timestamp " +
          "FROM instruments WHERE ticker='MSFT' RETURNING order_id",
          [accountId, side, status, at]);
        expect(result.rows).toHaveLength(1);
        return result.rows[0].order_id as number;
      };
      const account = accounts[0];
      const firstId = await insert(account.account_id, '2026-01-10T00:00:00');
      const lastId = await insert(account.account_id, '2026-01-11T23:59:59', 'FILLED', 'SELL');
      await insert(account.account_id, '2026-01-09T23:59:59');
      await insert(account.account_id, '2026-01-12T00:00:00');
      await insert(account.account_id, '2026-01-10T12:00:00', 'REJECTED');
      await insert(accounts[1].account_id, '2026-01-10T12:00:00');
      await use({ user: users[0], clientId: account.client_id, accountId: account.account_id, firstId, lastId });
    } finally {
      try {
        for (const user of users) {
          await db.query('DELETE FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1)', [user.username]);
          await db.query('DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
          await db.query('DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
          await db.query('DELETE FROM clients WHERE username=$1', [user.username]);
        }
      } finally { await db.end(); }
    }
  },
});

// The UI promises UTC dates even when the operator's browser uses another time zone.
test.use({ timezoneId: 'America/New_York' });

/** Signs in as Trading Ops, which lands on trade search: it is that role's start page. */
async function openTradeSearch(page: Page): Promise<void> {
  await signIn(page, TRADING_OPS);
  await expect(page).toHaveURL(/\/trade-search$/);
}

async function search(page: Page, expected: Record<string, string>) {
  const responsePromise = page.waitForResponse(response => new URL(response.url()).pathname === SEARCH);
  await page.getByRole('button', { name: 'Search trades', exact: true }).click();
  const response = await responsePromise;
  expect(response.status(), await response.text()).toBe(200);
  expect(response.request().method()).toBe('GET');
  expect(Object.fromEntries(new URL(response.url()).searchParams)).toEqual(expected);
  return response.json();
}

test('LMKT-39 AC1: Operations finds a stored trade by order ID', async ({ page, trades }) => {
  await openTradeSearch(page);
  await page.getByLabel('Order ID or client name').fill(String(trades.firstId));
  const result = await search(page, { orderId: String(trades.firstId) });
  expect(result).toHaveLength(1);
  expect(result[0]).toMatchObject({
    orderId: trades.firstId, clientId: trades.clientId, accountId: trades.accountId,
    symbol: 'MSFT', side: 'BUY', quantity: 2, pricePerUnit: 123.4567, filledAt: '2026-01-10T00:00:00',
  });
  const rows = page.locator('staff-trade-search tbody tr');
  await expect(rows).toHaveCount(1);
  await expect(rows.first().locator('td')).toHaveText([
    String(trades.firstId), String(trades.clientId), String(trades.accountId),
    'MSFT', 'BUY', '2', '123.4567', '2026-01-10 00:00:00 UTC',
  ]);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Trade search', exact: true })).toBeVisible();
});

test('LMKT-39 AC2: client/date search includes UTC boundaries and excludes other trades', async ({ page, trades }) => {
  await openTradeSearch(page);
  await page.getByRole('radio', { name: 'Client search' }).check();
  await page.getByLabel('Client ID or client name').fill(String(trades.clientId));
  await page.getByText('Optional client date range').click();
  await page.getByLabel('Start date (UTC)').fill('2026-01-10');
  await page.getByLabel('End date (UTC)').fill('2026-01-11');
  const result = await search(page, { clientId: String(trades.clientId), from: '2026-01-10', to: '2026-01-11' });
  expect(result.map((trade: { orderId: number }) => trade.orderId)).toEqual([trades.lastId, trades.firstId]);
  const rows = page.locator('staff-trade-search tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.locator('td:first-child')).toHaveText([String(trades.lastId), String(trades.firstId)]);
  await expect(rows.locator('td:nth-child(2)')).toHaveText([String(trades.clientId), String(trades.clientId)]);
  await expect(rows.locator('td:last-child')).toHaveText(['2026-01-11 23:59:59 UTC', '2026-01-10 00:00:00 UTC']);
  await page.getByLabel('Start date (UTC)').fill('2030-01-01');
  await page.getByLabel('End date (UTC)').fill('2030-01-01');
  expect(await search(page, { clientId: String(trades.clientId), from: '2030-01-01', to: '2030-01-01' })).toEqual([]);
  await expect(page.getByText('No trades found for these search criteria.')).toBeVisible();
  await expect(rows).toHaveCount(0);
});

test('LMKT-39 AC3: a client cannot query even their own trade', async ({ tradingApp, trades }) => {
  // The staff app turns a client away at the login (staff-login.spec.ts) and the staff gateway
  // refuses them (tests/staff-gateway.spec.ts). The trading gateway still routes the search
  // path, so it is the service's own rule that has to hold for the client's customer session.
  await loginViaApi(tradingApp, trades.user);
  const queries: Array<Record<string, string>> = [
    { orderId: String(trades.firstId) },
    { clientId: String(trades.clientId), from: '2026-01-10', to: '2026-01-11' },
  ];
  for (const params of queries) {
    const response = await tradingApp.get(SEARCH, { params });
    expect(response.status(), await response.text()).toBe(403);
  }
});
