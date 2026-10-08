import { APIRequestContext, test as base, expect, Page } from '@playwright/test';
import { connectDatabase } from '../tests/support/database';
import { loginViaApi } from '../tests/support/orders';
import { newUser, registerViaApi, TestUser } from '../tests/support/users';
import { signIn, TRADING_OPS, ANALYST, CLIENT } from './support/staff';

/**
 * LMKT-40 trade timeline, on the staff app. Signs in, fetches audit events and checks
 * authorization using the real staff app, staff gateway and backend, without mocks.
 */
const TIMELINE_API = (orderId: number) => `/api/v1/orders/${orderId}/timeline`;
// Clients register through the trading app; the staff gateway has no registration route.
const TRADING_APP = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

interface TradeFixture {
  user: TestUser;
  orderId: number;
  accountId: number;
}

const test = base.extend<{ tradingApp: APIRequestContext; trade: TradeFixture }>({
  tradingApp: async ({ playwright }, use) => {
    const context = await playwright.request.newContext({ baseURL: TRADING_APP });
    await use(context);
    await context.dispose();
  },
  trade: async ({ tradingApp }, use) => {
    const db = await connectDatabase();
    const user = newUser('timeline');
    try {
      await registerViaApi(tradingApp, user);
      const result = await db.query(
        'SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1',
        [user.username]
      );
      expect(result.rows).toHaveLength(1);
      const accountId = result.rows[0].account_id as number;

      // Insert a complete trade with all audit events
      const orderResult = await db.query(
        "INSERT INTO orders (account_id,instrument_id,order_type,quantity,price_per_unit,order_status,submitted_at,filled_at,created_at,updated_at) " +
        "SELECT $1,instrument_id,'BUY',10,100.50,'FILLED','2026-01-10T10:00:00'::timestamp,'2026-01-10T10:05:00'::timestamp,'2026-01-10T10:00:00'::timestamp,'2026-01-10T10:05:00'::timestamp " +
        "FROM instruments WHERE ticker='MSFT' RETURNING order_id",
        [accountId]
      );
      expect(orderResult.rows).toHaveLength(1);
      const orderId = orderResult.rows[0].order_id as number;

      // Insert audit events for the order
      const clientId = (await db.query('SELECT client_id FROM accounts WHERE account_id=$1', [accountId])).rows[0].client_id as number;
      // Use a unique requestId per test run to avoid constraint violations
      const requestId = crypto.randomUUID();
      
      await db.query(
        "INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at, request_id, event_key, archived) " +
        "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, false)",
        [orderId, accountId, clientId, 'SUBMITTED', JSON.stringify({side:'BUY',quantity:10,price:100.50,instrumentId:5}), '2026-01-10T10:00:00Z', requestId, `${requestId}:SUBMITTED`]
      );
      
      await db.query(
        "INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at, request_id, event_key, archived) " +
        "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, false)",
        [orderId, accountId, clientId, 'VALIDATED', JSON.stringify({rules:['ACCOUNT_ACCESS','TRADABLE','AVAILABLE_CASH']}), '2026-01-10T10:00:01Z', requestId, `${requestId}:VALIDATED`]
      );
      
      await db.query(
        "INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at, archived) " +
        "VALUES ($1, $2, $3, $4, $5, $6, false)",
        [orderId, accountId, clientId, 'ACCEPTED', JSON.stringify({}), '2026-01-10T10:00:02Z']
      );
      
      await db.query(
        "INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at, archived) " +
        "VALUES ($1, $2, $3, $4, $5, $6, false)",
        [orderId, accountId, clientId, 'FILLED', JSON.stringify({executedQuantity:10,executedPrice:100.50}), '2026-01-10T10:05:00Z']
      );

      await use({ user, orderId, accountId });
    } finally {
      try {
        // Delete audit_log events first, then orders (due to foreign key constraint)
        await db.query('DELETE FROM audit_log WHERE order_id IN (SELECT order_id FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1))', [user.username]);
        await db.query('DELETE FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1)', [user.username]);
        await db.query('DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
        await db.query('DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
        await db.query('DELETE FROM clients WHERE username=$1', [user.username]);
      } finally { await db.end(); }
    }
  },
});

test('LMKT-40 AC1: Trade timeline displays all audit events in chronological order', async ({ page, trade }) => {
  await signIn(page, TRADING_OPS);
  const response = await page.request.get(TIMELINE_API(trade.orderId));
  expect(response.status()).toBe(200);
  const events = await response.json();
  
  expect(events).toHaveLength(4);
  expect(events[0]).toMatchObject({ eventType: 'SUBMITTED' });
  expect(events[1]).toMatchObject({ eventType: 'VALIDATED' });
  expect(events[2]).toMatchObject({ eventType: 'ACCEPTED' });
  expect(events[3]).toMatchObject({ eventType: 'FILLED' });
  
  // Verify chronological order
  const times = events.map((e: { occurredAt: string }) => new Date(e.occurredAt).getTime());
  for (let i = 1; i < times.length; i++) {
    expect(times[i]).toBeGreaterThanOrEqual(times[i - 1]);
  }
});

test('LMKT-40 AC3: CLIENT role is denied access to trade timeline', async ({ tradingApp, trade }) => {
  await loginViaApi(tradingApp, trade.user);
  const response = await tradingApp.get(TIMELINE_API(trade.orderId));
  expect(response.status()).toBe(403);
});

test('LMKT-40 AC3: ANALYST role is denied access to trade timeline', async ({ page, trade }) => {
  await signIn(page, ANALYST);
  const response = await page.request.get(TIMELINE_API(trade.orderId));
  expect(response.status()).toBe(403);
});
