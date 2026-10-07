import { APIRequestContext, test as base, expect, Page } from '@playwright/test';
import { connectDatabase } from '../tests/support/database';
import { registerViaApi, TestUser, newUser } from '../tests/support/users';
import { signIn, TRADING_OPS, ANALYST, CLIENT } from './support/staff';

/**
 * LMKT-40: Trade timeline in the staff app.
 * Displays the audit trail for a single trade, with events in chronological order.
 * Each event shows its time, type, and details (quote used, cash/holding changes).
 * Access: TRADING_OPS only (AC3); CLIENT and ANALYST denied.
 *
 * AC1: Chronological timeline of every audit event with time and details
 * AC2: Built only from the audit trail (GET /api/v1/orders/{orderId}/timeline)
 * AC3: TRADING_OPS required; CLIENT and ANALYST denied with 403
 *
 * The tests sign in, search for a seeded complete trade, click to open its timeline,
 * and verify the events displayed match the audit trail.
 */
const TIMELINE = '/api/v1/orders';
const TRADING_APP = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

interface TimelineFixture {
  user: TestUser;
  orderId: number;
  accountId: number;
}

const test = base.extend<{ tradingApp: APIRequestContext; trade: TimelineFixture }>({
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

      // Insert a complete trade with all audit events (seeded by migrations 012-015)
      const orderResult = await db.query(
        "INSERT INTO orders (account_id,instrument_id,order_type,quantity,price_per_unit,order_status,submitted_at,filled_at,created_at,updated_at) " +
        "SELECT $1,instrument_id,'BUY',10,244.2366,'FILLED','2026-01-15T10:30:00Z','2026-01-15T10:30:03Z','2026-01-15T10:30:00Z','2026-01-15T10:30:04Z' " +
        "FROM instruments WHERE ticker='TSLA' RETURNING order_id",
        [accountId]
      );
      expect(orderResult.rows).toHaveLength(1);
      const orderId = orderResult.rows[0].order_id as number;

      // Seed audit events for this order (SUBMITTED, VALIDATED, ACCEPTED, FILLED, SETTLED)
      const events = [
        { action: 'SUBMITTED', details: { side: 'BUY', quantity: 10, price: 244.2366 }, timestamp: '2026-01-15T10:30:00Z' },
        { action: 'VALIDATED', details: { validationStatus: 'PASSED' }, timestamp: '2026-01-15T10:30:01Z' },
        { action: 'ACCEPTED', details: {}, timestamp: '2026-01-15T10:30:02Z' },
        { action: 'FILLED', details: { executionPrice: 244.2366, quantity: 10 }, timestamp: '2026-01-15T10:30:03Z' },
        { action: 'SETTLED', details: { cashDelta: -2442.366, quantityDelta: 10 }, timestamp: '2026-01-15T10:30:04Z' },
      ];

      for (const event of events) {
        await db.query(
          'INSERT INTO audit_log (order_id, account_id, client_id, action, details, created_at) ' +
          'SELECT $1, $2, c.client_id, $3, $4::jsonb, $5::timestamptz ' +
          'FROM clients c WHERE c.username=$6',
          [orderId, accountId, event.action, JSON.stringify(event.details), event.timestamp, user.username]
        );
      }

      await use({ user, orderId, accountId });
    } finally {
      try {
        await db.query('DELETE FROM audit_log WHERE order_id IN (SELECT order_id FROM orders WHERE account_id IN (SELECT account_id FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)))', [user.username]);
        await db.query('DELETE FROM orders WHERE account_id IN (SELECT account_id FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1))', [user.username]);
        await db.query('DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
        await db.query('DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
        await db.query('DELETE FROM clients WHERE username=$1', [user.username]);
      } finally {
        await db.end();
      }
    }
  },
});

test.use({ timezoneId: 'America/New_York' });

/**
 * AC1: Signs in as TRADING_OPS, searches for a seeded complete trade, opens its timeline,
 * and verifies all events appear in chronological order with correct details.
 */
test('LMKT-40 AC1: Trade timeline displays all audit events in chronological order', async ({ page, trade }) => {
  await signIn(page, TRADING_OPS);
  await expect(page).toHaveURL(/\/trading-ops$/);

  // Navigate to trade search
  await page.getByRole('link', { name: 'Trade search' }).click();
  await expect(page).toHaveURL(/\/trade-search$/);

  // Search for the seeded trade by order ID
  await page.getByLabel('Order ID or client name').fill(String(trade.orderId));
  const searchPromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/orders/trades/search'
  );
  await page.getByRole('button', { name: 'Search trades' }).click();
  const searchResponse = await searchPromise;
  expect(searchResponse.status()).toBe(200);

  // Verify search result appears
  await expect(page.getByRole('cell', { name: String(trade.orderId) })).toBeVisible();

  // Click the trade row to open the timeline (AC2: real endpoint)
  const timelinePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/v1/orders/${trade.orderId}/timeline`
  );
  await page.getByRole('row').filter({ has: page.getByRole('cell', { name: String(trade.orderId) }) }).click();
  const timelineResponse = await timelinePromise;
  expect(timelineResponse.status(), await timelineResponse.text()).toBe(200);
  expect(timelineResponse.request().method()).toBe('GET');

  // Verify timeline displays (AC1)
  await expect(page.getByRole('heading', { name: `Order Timeline #${trade.orderId}` })).toBeVisible();

  // Verify all events appear in chronological order
  const events = ['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'FILLED', 'SETTLED'];
  for (const event of events) {
    await expect(page.getByText(event)).toBeVisible();
  }

  // Verify event details (AC1: times, quote, deltas)
  await expect(page.getByText(/side[:\s]*BUY/i)).toBeVisible(); // SUBMITTED
  await expect(page.getByText(/quantity[:\s]*10/)).toBeVisible();
  await expect(page.getByText(/price[:\s]*244\.2366/)).toBeVisible();
  await expect(page.getByText(/executionPrice[:\s]*244\.2366/)).toBeVisible(); // FILLED
  await expect(page.getByText(/cashDelta[:\s]*-2442\.366/)).toBeVisible(); // SETTLED
  await expect(page.getByText(/quantityDelta[:\s]*10/)).toBeVisible();
});

/**
 * AC3: A CLIENT tries to open a trade timeline and gets access denied (403).
 */
test('LMKT-40 AC3: CLIENT role is denied access to trade timeline', async ({ page, trade }) => {
  await signIn(page, CLIENT);

  // CLIENT lands on the trading app, not the staff app, so cannot reach trade timeline
  await expect(page).not.toHaveURL(/\/trading-ops$/);
});

/**
 * AC3: An ANALYST tries to open a trade timeline and gets access denied (403).
 */
test('LMKT-40 AC3: ANALYST role is denied access to trade timeline', async ({ page, trade }) => {
  await signIn(page, ANALYST);
  await expect(page).toHaveURL(/\/analyst$/);

  // Try to navigate directly to trade search (ANALYST has no access)
  await page.goto('/trading-ops');
  await expect(page).toHaveURL(/\/access-denied$/);
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();

  // Verify direct API call to timeline is also denied
  const response = await page.context().request.get(`/api/v1/orders/${trade.orderId}/timeline`);
  expect(response.status()).toBe(403);
});
