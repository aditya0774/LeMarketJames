import { test as base, expect, Page } from '@playwright/test';
import { connectDatabase } from './support/database';
import { openTab } from './support/nav';
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
    const db = await connectDatabase();
    const user = newUser('history');
    try {
      await registerViaApi(request, user);
      const account = await db.query(`SELECT a.account_id FROM accounts a
        JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1`, [user.username]);
      const accountId = account.rows[0].account_id;
      for (const [index, timestamp] of timestamps.entries()) {
        await db.query(`INSERT INTO orders
          (account_id,instrument_id,order_type,quantity,price_per_unit,order_status,rejection_reason,
           submitted_at,filled_at,created_at,updated_at)
          SELECT $1,instrument_id,'BUY',$2,100,$3,$4,$5::timestamp,
            '2027-01-01'::timestamp,$5::timestamp,$5::timestamp
          FROM instruments WHERE ticker='AAPL'`,
          [
            accountId,
            index + 1,
            index === 0 ? 'REJECTED' : 'FILLED',
            index === 0 ? 'INSUFFICIENT_HOLDINGS' : null,
            timestamp,
          ]);
      }
      await loginViaUi(page, user);
      await expect(page).toHaveURL(/dashboard/);
      // Every fixture is filled or rejected, so the Overview counts no open orders.
      await expect(page.locator('.stat-card .val').last()).toHaveText('0');
      await openTab(page, 'Orders');
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
    return url.pathname.includes('/orders/account/')
      && url.searchParams.get('date') === date
      && url.searchParams.get('page') === '0'
      && url.searchParams.get('size') === '6';
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
    await expect(rows.locator('td:nth-child(4)')).toHaveText(scenario.quantities.map(String));
    if (scenario.display) {
      await expect(rows.locator('td:nth-child(1)')).toContainText(scenario.quantities.map(() => scenario.display!));
    }
  });
}

test('AC2: no matching orders shows the exact empty message', async ({ page }) => {
  expect(await choose(page, 'year', '2030')).toEqual([]);
  await expect(page.getByText('No orders in this period', { exact: true })).toBeVisible();
  await expect(page.locator('app-orders-panel tbody tr[data-order-id]')).toHaveCount(0);
});

test('AC3: clearing restores full history and resets status and pagination', async ({ page }) => {
  const panel = page.locator('app-orders-panel');
  const nextPage = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.includes('/orders/account/')
      && url.searchParams.get('page') === '1'
      && url.searchParams.get('size') === '6';
  });
  await panel.getByRole('button', { name: 'Next page' }).click();
  await nextPage;
  await choose(page, 'year', '2030');
  await panel.getByRole('button', { name: 'Rejected', exact: true }).click();
  const response = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.includes('/orders/account/')
      && !url.searchParams.get('date')
      && !url.searchParams.get('timeZone')
      && url.searchParams.get('page') === '0'
      && url.searchParams.get('size') === '6';
  });
  await panel.getByRole('button', { name: 'Clear filter' }).click();
  const received = await response;
  expect(received.status()).toBe(200);
  expect(await received.json()).toHaveLength(6);
  await expect(panel.locator('.pg-info')).toContainText('Showing 1–5 of 15 orders');
  await expect(panel.getByRole('button', { name: 'All', exact: true })).toHaveAttribute('aria-pressed', 'true');
  const firstVisibleByPage = [15, 9, 3];
  const quantities: string[] = [];
  for (let pageIndex = 0; pageIndex < 3; pageIndex++) {
    await expect(panel.locator('tbody tr[data-order-id] td:nth-child(4)').first())
      .toHaveText(String(firstVisibleByPage[pageIndex]));
    quantities.push(...await panel.locator('tbody tr[data-order-id] td:nth-child(4)').allTextContents());
    if (pageIndex < 2) {
      const pageResponse = page.waitForResponse(response => {
        const url = new URL(response.url());
        return url.pathname.includes('/orders/account/')
          && !url.searchParams.get('date')
          && !url.searchParams.get('timeZone')
          && url.searchParams.get('page') === String(pageIndex + 1)
          && url.searchParams.get('size') === '6';
      });
      await panel.getByRole('button', { name: 'Next page' }).click();
      await pageResponse;
    }
  }
  expect(quantities.map(value => Number(value.trim())))
    .toEqual([15, 14, 13, 12, 11, 9, 8, 7, 6, 5, 3, 2, 1]);
});

test('AC4: order-history rows show required columns and status-specific values', async ({ page }) => {
  const panel = page.locator('app-orders-panel');

  await choose(page, 'day', '2026-11-01');

  await expect(panel.locator('thead th')).toHaveText(['When', 'Stock', 'Type', 'Shares', 'Status', 'Filled at']);

  const filledRow = panel.locator('tbody tr[data-order-id]').first();
  await expect(filledRow.locator('td:nth-child(2) .sym')).toHaveText('AAPL');
  await expect(filledRow.locator('td:nth-child(3)')).toHaveText('BUY');
  await expect(filledRow.locator('td:nth-child(4)')).toHaveText('14');
  // The Status cell holds the badge and a note under it: the amount for a fill, the reason for a rejection.
  await expect(filledRow.locator('td:nth-child(5)')).toContainText('Filled');
  await expect(filledRow.locator('td:nth-child(5)')).toContainText('Done. You paid $1,400.00.');
  await expect(filledRow.locator('td:nth-child(6)')).toContainText('$100.00');

  await choose(page, 'year', '2023');

  const rejectedRow = panel.locator('tbody tr[data-order-id]').first();
  await expect(rejectedRow.locator('td:nth-child(2) .sym')).toHaveText('AAPL');
  await expect(rejectedRow.locator('td:nth-child(3)')).toHaveText('BUY');
  await expect(rejectedRow.locator('td:nth-child(4)')).toHaveText('1');
  await expect(rejectedRow.locator('td:nth-child(5)')).toContainText('Rejected');
  await expect(rejectedRow.locator('td:nth-child(5)'))
    .toContainText('Order rejected because you do not hold enough shares to sell that quantity.');
  await expect(rejectedRow.locator('td:nth-child(6)')).toHaveText('—');
});
