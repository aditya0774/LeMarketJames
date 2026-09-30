import { test as base, expect, Page } from '@playwright/test';
import { Client } from 'pg';
import { loginViaUi, newUser, registerViaApi } from './support/users';

// UTC fixtures span years, leap day, and both US DST transitions. Quantity identifies
// each fixture in assertions without depending on database-generated order IDs.
const timestamps = [
  '2023-12-31T23:00:00',
  '2024-02-01T05:00:00', '2024-02-29T05:00:00', '2024-03-01T04:59:59',
  '2024-03-01T05:00:00', '2025-01-01T04:59:59', '2025-01-01T05:00:00',
  '2026-01-01T04:59:59', '2026-03-08T04:59:59', '2026-03-08T05:00:00',
  '2026-03-09T03:59:59', '2026-03-09T04:00:00',
  '2026-11-01T04:00:00', '2026-11-02T04:59:59', '2026-11-02T05:00:00',
];

const test = base.extend<{ seededAccount: number }>({
  seededAccount: [async ({ page, request }, use) => {
    const connectionString = process.env.E2E_DATABASE_URL;
    if (!connectionString && process.env.E2E_ALLOW_DATABASE_SEED !== 'true') {
      throw new Error('Set E2E_DATABASE_URL or explicitly allow seeding a disposable database.');
    }
    // node-postgres uses PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD when no URL is supplied.
    const db = connectionString ? new Client({ connectionString }) : new Client();
    await db.connect();
    const user = newUser('history');
    try {
      await registerViaApi(request, user);
      const account = await db.query(`SELECT a.account_id FROM accounts a
        JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1`, [user.username]);
      const accountId = account.rows[0].account_id;
      for (const [index, timestamp] of timestamps.entries()) {
        await db.query(`INSERT INTO orders
          (account_id,instrument_id,order_type,quantity,price_per_unit,order_status,
           submitted_at,filled_at,created_at,updated_at)
          SELECT $1,instrument_id,'BUY',$2,100,$3,$4::timestamp,
            '2027-01-01'::timestamp,$4::timestamp,$4::timestamp
          FROM instruments WHERE ticker='AAPL'`,
          [accountId, index + 1, index === 0 ? 'SUBMITTED' : 'FILLED', timestamp]);
      }
      await loginViaUi(page, user);
      await expect(page).toHaveURL(/dashboard/);
      await expect(page.locator('app-orders-panel .pg-info')).toContainText('of 15 orders');
      await use(accountId);
    } finally {
      // Remove only this test's registered client. Never truncate the shared test database.
      await db.query(`DELETE FROM orders WHERE account_id IN
        (SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1)`, [user.username]);
      await db.query('DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
      await db.query('DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [user.username]);
      await db.query('DELETE FROM clients WHERE username=$1', [user.username]);
      await db.end();
    }
  }, { auto: true }],
});

test.use({ timezoneId: 'America/New_York' });

async function choose(page: Page, period: string, date: string) {
  const panel = page.locator('app-orders-panel');
  await panel.getByLabel('Period', { exact: true }).selectOption(period);
  const response = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.includes('/orders/account/') && url.searchParams.get('date') === date;
  });
  await panel.locator('.date-filters input').fill(date);
  const received = await response;
  expect(received.status()).toBe(200);
  expect(new URL(received.url()).searchParams.get('timeZone')).toBe('America/New_York');
  return received.json();
}

for (const scenario of [
  { period: 'day', date: '2026-03-08', quantities: [11, 10], display: 'Mar 8, 2026' },
  { period: 'day', date: '2026-11-01', quantities: [14, 13], display: 'Nov 1, 2026' },
  { period: 'day', date: '2024-02-29', quantities: [4, 3], display: 'Feb 29, 2024' },
  { period: 'month', date: '2024-02', quantities: [4, 3, 2] },
  { period: 'year', date: '2025', quantities: [8, 7] },
]) {
  test(`AC1: ${scenario.period} ${scenario.date} uses local placement dates`, async ({ page }) => {
    const result = await choose(page, scenario.period, scenario.date);
    expect(result.map((order: { quantity: number }) => order.quantity).sort((a: number, b: number) => b - a))
      .toEqual(scenario.quantities);
    const rows = page.locator('app-orders-panel tbody tr[data-order-id]');
    await expect(rows).toHaveCount(scenario.quantities.length);
    await expect(rows.locator('td:nth-child(3)')).toHaveText(scenario.quantities.map(String));
    if (scenario.display) {
      await expect(rows.locator('td:last-child')).toHaveText(scenario.quantities.map(() => scenario.display!));
    }
    // Filtering history must not change the account-wide open-order count.
    await expect(page.locator('.stat-card .val').last()).toHaveText('1');
  });
}

test('AC2: no matching orders shows the exact empty message', async ({ page }) => {
  expect(await choose(page, 'year', '2030')).toEqual([]);
  await expect(page.getByText('No orders in this period', { exact: true })).toBeVisible();
  await expect(page.locator('app-orders-panel tbody tr[data-order-id]')).toHaveCount(0);
});

test('AC3: clearing restores full history and resets status and pagination', async ({ page }) => {
  const panel = page.locator('app-orders-panel');
  await panel.getByRole('button', { name: 'Next page' }).click();
  await choose(page, 'year', '2030');
  await panel.getByRole('button', { name: 'Rejected', exact: true }).click();
  const response = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.includes('/orders/account/') && !url.search;
  });
  await panel.getByRole('button', { name: 'Clear filter' }).click();
  const received = await response;
  expect(received.status()).toBe(200);
  expect(await received.json()).toHaveLength(15);
  await expect(panel.locator('.pg-info')).toContainText(/Showing 1.5 of 15 orders/);
  await expect(panel.getByRole('button', { name: 'All', exact: true })).toHaveAttribute('aria-pressed', 'true');
  const quantities: string[] = [];
  for (let pageIndex = 0; pageIndex < 3; pageIndex++) {
    await expect(panel.locator('tbody tr[data-order-id] td:nth-child(3)').first())
      .toHaveText(String(15 - pageIndex * 5));
    quantities.push(...await panel.locator('tbody tr[data-order-id] td:nth-child(3)').allTextContents());
    if (pageIndex < 2) await panel.getByRole('button', { name: 'Next page' }).click();
  }
  expect(quantities.map(value => Number(value.trim())))
    .toEqual(Array.from({ length: 15 }, (_, i) => 15 - i));
});
