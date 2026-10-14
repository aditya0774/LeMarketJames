import { expect, Page, test } from '@playwright/test';
import { connectDatabase } from '../tests/support/database';
import { ANALYST, CLIENT, signIn, TRADING_OPS } from './support/staff';

const REPORT = '/analyst/reports/activity-by-client-segment';
const API = '/api/v1/reports/activity/comparison';
const CONTENT = 'staff-activity-by-client-segment';
const REPORT_LIMIT_MS = 10_000;

interface SegmentRow {
  segment: string;
  activeClients: number;
  tradeCount: number;
  totalTradedValue: number;
}

// Historical dates exclude today's executions, so other suites cannot change the oracle.
// Segment definitions come from C3's persisted data, never a second hard-coded list.
function daysAgo(days: number): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

async function openReport(page: Page) {
  await signIn(page, ANALYST);
  await expect(page).toHaveURL(/\/analyst$/);
  const response = page.waitForResponse(r => new URL(r.url()).pathname === API);
  await page.locator('.report-card', { hasText: 'Activity by Client Segment' }).click();
  expect((await response).status()).toBe(200);
  await expect(page).toHaveURL(new RegExp(`${REPORT}$`));
  await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toBeEnabled();
}

async function chooseRange(page: Page, start: string, end: string) {
  for (const [label, value] of [['Start Date', start], ['End Date', end]]) {
    const [year, month, day] = value.split('-');
    const input = page.getByLabel(label, { exact: true });
    const current = new Date(await input.inputValue());
    if (current.getFullYear() === Number(year) && current.getMonth() + 1 === Number(month)
      && current.getDate() === Number(day)) continue;
    await input.fill(`${month}/${day}/${year}`);
    await input.press('Tab');
    await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toBeEnabled();
  }
}

async function refresh(page: Page, start: string, end: string): Promise<SegmentRow[]> {
  const responsePromise = page.waitForResponse(r => {
    const url = new URL(r.url());
    return url.pathname === API && url.searchParams.get('startDate') === start
      && url.searchParams.get('endDate') === end;
  });
  await page.getByRole('button', { name: 'Refresh', exact: true }).click();
  const response = await responsePromise;
  expect(response.status(), await response.text()).toBe(200);
  expect(response.request().method()).toBe('GET');
  expect(new URL(response.url()).origin).toBe(new URL(page.url()).origin);
  const body = await response.json();
  // An allowlist catches new nested identifiers as well as known personal-data fields.
  expect(Object.keys(body).sort()).toEqual([
    'allTimeData', 'comparison', 'comparisonEndDate', 'comparisonStartDate', 'data', 'endDate', 'startDate', 'success', 'timeZone',
  ]);
  for (const row of body.comparison) {
    expect(Object.keys(row).sort()).toEqual(['activeClients', 'current', 'previous', 'segment', 'totalTradedValue', 'tradeCount']);
    for (const period of [row.current, row.previous]) {
      expect(Object.keys(period).sort()).toEqual(['activeClients', 'segment', 'totalTradedValue', 'tradeCount']);
    }
    for (const field of ['activeClients', 'tradeCount', 'totalTradedValue']) {
      expect(Object.keys(row[field]).sort()).toEqual(['absolute', 'percent']);
      expect(row[field].absolute).toBeCloseTo(row.current[field] - row.previous[field], 6);
      if (row.previous[field] === 0) expect(row[field].percent).toBeNull();
      else expect(row[field].percent).toBeCloseTo(row[field].absolute / row.previous[field] * 100, 2);
    }
  }
  expect(body.success).toBe(true);
  for (const row of [...body.data, ...body.allTimeData]) {
    expect(Object.keys(row).sort()).toEqual(['activeClients', 'segment', 'totalTradedValue', 'tradeCount']);
    expect(typeof row.segment).toBe('string');
    for (const field of ['activeClients', 'tradeCount', 'totalTradedValue']) {
      expect(typeof row[field]).toBe('number');
    }
  }
  return body.data;
}

/** Independent oracle: fold raw historical fills in the test, not the API's grouped query. */
async function expectedRows(page: Page, start: string, end: string, allTime = false): Promise<SegmentRow[]> {
  const ping = await page.request.get('/api/v1/reports/ping');
  expect(ping.status()).toBe(200);
  const { timeZone } = await ping.json();
  const db = await connectDatabase();
  try {
    const { rows } = await db.query(
      `SELECT segment, client_id, gross_amount::text AS amount FROM reporting_trades
       WHERE $4::boolean OR (filled_at AT TIME ZONE 'UTC' AT TIME ZONE $3)::date BETWEEN $1::date AND $2::date`,
      [start, end, timeZone, allTime]);
    const groups = new Map<string, { clients: Set<number>; trades: number; value: bigint }>();
    for (const row of rows) {
      const group = groups.get(row.segment) ?? { clients: new Set<number>(), trades: 0, value: 0n };
      group.clients.add(row.client_id);
      group.trades++;
      // The view multiplies two four-decimal NUMERICs; sum exactly before converting to JSON numbers.
      const [whole, fraction = ''] = row.amount.split('.');
      group.value += BigInt(whole) * 100_000_000n + BigInt(fraction.padEnd(8, '0'));
      groups.set(row.segment, group);
    }
    return [...groups].sort(([a], [b]) => a.localeCompare(b)).map(([segment, group]) => ({
      segment, activeClients: group.clients.size, tradeCount: group.trades,
      totalTradedValue: Number(group.value) / 100_000_000,
    }));
  } finally { await db.end(); }
}

async function expectChart(page: Page, rows: SegmentRow[]) {
  const chart = page.locator(`${CONTENT} .chart-body`);
  await expect(chart.locator('.chart-row')).toHaveCount(rows.length);
  const number = new Intl.NumberFormat('en-US');
  const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  for (const row of rows) {
    const label = row.segment.toLowerCase().split('_').map(word => word[0].toUpperCase() + word.slice(1)).join(' ');
    const rendered = chart.locator('.chart-row').filter({ has: page.getByText(label, { exact: true }) });
    await expect(rendered.locator('.column-value')).toHaveText(currency.format(row.totalTradedValue));
    await expect(rendered.locator('.column-meta')).toContainText(`${number.format(row.activeClients)} unique trading clients`);
    await expect(rendered.locator('.column-meta')).toContainText(`${number.format(row.tradeCount)} trades`);
  }
}

test('LMKT-43 AC1/AC3: selected dates show real segment aggregates without client details', async ({ page }) => {
  await openReport(page);
  const end = daysAgo(2);
  const results: SegmentRow[][] = [];
  for (const start of [daysAgo(400), daysAgo(200)]) {
    const expected = await expectedRows(page, start, end);
    expect(expected.length, 'C3 must supply multiple segments with historical fills').toBeGreaterThan(1);
    await chooseRange(page, start, end);
    const actual = await refresh(page, start, end);
    expect(actual).toEqual(expected);
    await expectChart(page, await expectedRows(page, start, end, true));
    results.push(actual);
  }
  expect(results[0]).not.toEqual(results[1]);
  const content = page.locator(`${CONTENT} .report-content`);
  await expect(content.locator('th')).toHaveText([
    /Segment/, /Active Clients/, /Trade Count/, /Total Traded Value/,
  ]);
  await expect(content.locator('a, button, input')).toHaveCount(0);
  await expect(content).not.toContainText(/@|client\s*id|account\s*id|order\s*id|email|phone|SSN/i);
});

test('LMKT-43 AC2: a typical real report renders within ten seconds', async ({ page }) => {
  await openReport(page);
  const start = daysAgo(35), end = daysAgo(2);
  await chooseRange(page, start, end);
  // Login, initial navigation and date editing are setup, outside the report-generation SLA.
  const allTimeRows = await expectedRows(page, start, end, true);
  const started = performance.now();
  const rows = await refresh(page, start, end);
  expect(rows.length).toBeGreaterThan(0);
  await expectChart(page, allTimeRows);
  expect(performance.now() - started).toBeLessThan(REPORT_LIMIT_MS);
});

test('LMKT-43 AC1: an empty historical range shows the real empty state', async ({ page }) => {
  await openReport(page);
  const start = '1900-01-01', end = '1900-01-02';
  await chooseRange(page, start, end);
  expect(await refresh(page, start, end)).toEqual([]);
  await expect(page.getByRole('heading', { name: 'No segment activity in this range' })).toBeVisible();
  await expectChart(page, await expectedRows(page, start, end, true));
});

test('LMKT-43 AC4: signed-out visitors cannot open the page or request data', async ({ page }) => {
  expect((await page.request.get(API)).status()).toBe(401);
  await page.goto(REPORT);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator(CONTENT)).toHaveCount(0);
});

for (const [role, user] of [['CLIENT', CLIENT], ['TRADING_OPS', TRADING_OPS]] as const) {
  test(`LMKT-43 AC4: ${role} cannot open the page or bypass it with the API`, async ({ page }) => {
    await signIn(page, user);
    if (role === 'CLIENT') {
      await expect(page.getByRole('alert')).toContainText('This login is for staff only');
    } else {
      await expect(page).toHaveURL(/\/trade-search$/);
    }
    // Authenticate directly as well: UI sign-out must not hide a missing server-side role check.
    const login = await page.request.post('/api/auth/login', {
      data: { username: user.email, password: user.password },
    });
    expect(login.status()).toBe(200);
    expect((await page.request.get('/api/auth/me')).status()).toBe(200);
    expect((await page.request.get(API)).status()).toBe(403);
    await page.goto(REPORT);
    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    await expect(page.locator(CONTENT)).toHaveCount(0);
  });
}
