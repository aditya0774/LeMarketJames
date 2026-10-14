import { expect, Page, Response, test } from '@playwright/test';
import { Client } from 'pg';
import { connectDatabase } from '../tests/support/database';
import { newUser, registerViaApi, TestUser } from '../tests/support/users';
import { ANALYST, CLIENT, signIn, TRADING_OPS } from './support/staff';

/**
 * LMKT-46: the volume insight tile on the Analyst dashboard, end to end (S38-INT). Everything
 * goes through the real staff app, staff gateway and reporting-service, without mocks.
 *
 * AC1  the tile compares the last completed day and week with the period before, from real data
 * AC2  the tile shows when the figures were last refreshed
 * AC3  an Analyst sees it; a client and Trading Ops are denied
 *
 * The seed fills are monthly, so yesterday and last week would hold no trades and every figure
 * would be zero. A throwaway client therefore gets fills placed in those periods (the order-history
 * and trade-search specs do the same), and the expected figures are folded from reporting_trades
 * in the test, not taken from the API under test. Only data setup uses SQL.
 */
const INSIGHT = '/api/v1/reports/insights/volume';
// Clients register through the trading app; the staff gateway has no registration route.
const TRADING_APP = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const TILE = 'staff-volume-insight-tile';
const SECTIONS = { DAY: 'Yesterday', WEEK: 'Last week' } as const;
type PeriodType = keyof typeof SECTIONS;

interface Totals { startDate: string; endDate: string; tradeCount: number; shareVolume: number; grossAmount: number }
interface Insight {
  periodType: PeriodType;
  current: Totals;
  previous: Totals;
  change: { shareVolume: number; shareVolumePercent: number | null; grossAmount: number; grossAmountPercent: number | null };
  generatedAt: string;
}

// ---- Calendar: whole days, weeks (Monday to Sunday) in the reports time zone, as in C6 ----

/** A calendar day as YYYY-MM-DD, moved by whole days. UTC arithmetic keeps DST out of it. */
function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function todayIn(timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

/** The completed day and week before `today`, each with the period before it. */
function periodsFor(today: string): Record<PeriodType, { current: [string, string]; previous: [string, string] }> {
  const sinceMonday = (new Date(`${today}T00:00:00Z`).getUTCDay() + 6) % 7;
  const thisMonday = addDays(today, -sinceMonday);
  return {
    DAY: {
      current: [addDays(today, -1), addDays(today, -1)],
      previous: [addDays(today, -2), addDays(today, -2)],
    },
    WEEK: {
      current: [addDays(thisMonday, -7), addDays(thisMonday, -1)],
      previous: [addDays(thisMonday, -14), addDays(thisMonday, -8)],
    },
  };
}

// ---- Fixtures: one throwaway client with fills in the periods the tile compares ----

interface Fill { day: string; side: 'BUY' | 'SELL'; quantity: number; price: number }

/**
 * Yesterday beats the day before, so the day shows an increase. Last week is far smaller than the
 * week before, so the week shows a decrease; the gap is wide enough that the fills above (which can
 * fall in either week, depending on the weekday) or other suites' fills cannot turn it around.
 */
function fillsFor(periods: ReturnType<typeof periodsFor>): Fill[] {
  return [
    { day: periods.DAY.current[0], side: 'BUY', quantity: 2000, price: 100 },
    { day: periods.DAY.current[0], side: 'SELL', quantity: 1000, price: 150 },
    { day: periods.DAY.previous[0], side: 'BUY', quantity: 1000, price: 200 },
    { day: periods.WEEK.current[0], side: 'BUY', quantity: 1000, price: 120 },
    { day: periods.WEEK.previous[0], side: 'BUY', quantity: 50000, price: 10 },
  ];
}

async function insertFill(db: Client, accountId: number, timeZone: string, fill: Fill): Promise<void> {
  // Noon on the report day in the reports time zone, converted to the UTC wall clock the table stores.
  const result = await db.query(
    `INSERT INTO orders (account_id, instrument_id, order_type, quantity, price_per_unit, order_status,
                         submitted_at, accepted_at, filled_at, created_at, updated_at)
     SELECT $1, i.instrument_id, $2, $3, $4, 'FILLED', t.at, t.at, t.at, t.at, t.at
     FROM instruments i,
          (SELECT (($5::date + time '12:00') AT TIME ZONE $6::text) AT TIME ZONE 'UTC' AS at) t
     WHERE i.ticker = 'AAPL'`,
    [accountId, fill.side, fill.quantity, fill.price, fill.day, timeZone]);
  expect(result.rowCount).toBe(1);
}

async function removeClient(db: Client, username: string): Promise<void> {
  // Only this test's client; never truncate the shared database.
  await db.query('DELETE FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1)', [username]);
  await db.query('DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [username]);
  await db.query('DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [username]);
  await db.query('DELETE FROM clients WHERE username=$1', [username]);
}

// ---- Oracle: the same figures, folded from the data source in the test ----

async function expectedTotals(db: Client, timeZone: string, [start, end]: [string, string]): Promise<Totals> {
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS trades, COALESCE(SUM(quantity), 0)::text AS shares, COALESCE(SUM(gross_amount), 0)::text AS gross
     FROM reporting_trades
     WHERE (filled_at AT TIME ZONE 'UTC' AT TIME ZONE $3::text)::date BETWEEN $1::date AND $2::date`,
    [start, end, timeZone]);
  return { startDate: start, endDate: end, tradeCount: rows[0].trades, shareVolume: Number(rows[0].shares), grossAmount: Number(rows[0].gross) };
}

const percent = (change: number, previous: number): number | null =>
  previous === 0 ? null : Math.round((change / previous) * 10000) / 100;

// ---- Reading the page ----

const number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 3 });
const percentText = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const signed = (value: number, text: string) => `${value > 0 ? '+' : ''}${text}`;
const squash = (text: string | null) => (text ?? '').replace(/\s+/g, ' ').trim();

function insightResponse(page: Page, periodType: PeriodType): Promise<Response> {
  return page.waitForResponse(r => {
    const url = new URL(r.url());
    return url.pathname === INSIGHT && url.searchParams.get('periodType') === periodType;
  });
}

async function read(response: Response): Promise<Insight> {
  expect(response.status(), await response.text()).toBe(200);
  expect(response.request().method()).toBe('GET');
  return response.json();
}

const section = (page: Page, periodType: PeriodType) => page.locator(`${TILE} [data-period="${periodType}"]`);

test.describe('LMKT-46 volume insight', () => {
  // One set of fixtures serves every test; no other spec reads the insight, so nothing races them.
  test.describe.configure({ mode: 'serial' });

  let db: Client;
  let timeZone: string;
  let periods: ReturnType<typeof periodsFor>;
  let client: TestUser;

  test.beforeAll(async ({ playwright }) => {
    db = await connectDatabase();
    client = newUser('volume');
    const tradingApp = await playwright.request.newContext({ baseURL: TRADING_APP });
    const staffApp = await playwright.request.newContext({ baseURL: process.env.E2E_STAFF_BASE_URL ?? 'http://localhost:4201' });
    try {
      await registerViaApi(tradingApp, client);
      // The reports time zone is a setting (C5), so ask the service instead of assuming one.
      const login = await staffApp.post('/api/auth/login', { data: { username: ANALYST.email, password: ANALYST.password } });
      expect(login.status(), await login.text()).toBe(200);
      const ping = await staffApp.get('/api/v1/reports/ping');
      expect(ping.status()).toBe(200);
      timeZone = (await ping.json()).timeZone;

      periods = periodsFor(todayIn(timeZone));
      const { rows } = await db.query(
        'SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1', [client.username]);
      expect(rows).toHaveLength(1);
      for (const fill of fillsFor(periods)) await insertFill(db, rows[0].account_id, timeZone, fill);
    } catch (error) {
      await removeClient(db, client.username);
      await db.end();
      throw error;
    } finally {
      await tradingApp.dispose();
      await staffApp.dispose();
    }
  });

  test.afterAll(async () => {
    try { await removeClient(db, client.username); } finally { await db.end(); }
  });

  test('AC1: the tile compares the last day and week with the period before, from real data', async ({ page }) => {
    const responses = { DAY: insightResponse(page, 'DAY'), WEEK: insightResponse(page, 'WEEK') };
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);

    for (const periodType of ['DAY', 'WEEK'] as const) {
      const response = await responses[periodType];
      expect(new URL(response.url()).origin).toBe(new URL(page.url()).origin);
      const body = await read(response);

      // An allowlist catches new identifiers as well as known personal-data fields.
      expect(Object.keys(body).sort()).toEqual(['change', 'current', 'generatedAt', 'periodType', 'previous']);
      for (const totals of [body.current, body.previous]) {
        expect(Object.keys(totals).sort()).toEqual(['endDate', 'grossAmount', 'shareVolume', 'startDate', 'tradeCount']);
      }
      expect(Object.keys(body.change).sort()).toEqual(['grossAmount', 'grossAmountPercent', 'shareVolume', 'shareVolumePercent']);
      expect(body.periodType).toBe(periodType);

      const current = await expectedTotals(db, timeZone, periods[periodType].current);
      const previous = await expectedTotals(db, timeZone, periods[periodType].previous);
      expect(current.shareVolume, 'the fixtures put trades in the period').toBeGreaterThan(0);
      expect(previous.shareVolume, 'the fixtures put trades in the previous period').toBeGreaterThan(0);
      for (const [actual, expected] of [[body.current, current], [body.previous, previous]] as const) {
        expect(actual).toMatchObject({
          startDate: expected.startDate, endDate: expected.endDate,
          tradeCount: expected.tradeCount, shareVolume: expected.shareVolume,
        });
        expect(actual.grossAmount).toBeCloseTo(expected.grossAmount, 2);
      }
      const volumeChange = current.shareVolume - previous.shareVolume;
      const grossChange = current.grossAmount - previous.grossAmount;
      expect(body.change.shareVolume).toBe(volumeChange);
      expect(body.change.grossAmount).toBeCloseTo(grossChange, 2);
      expect(body.change.shareVolumePercent).toBeCloseTo(percent(volumeChange, previous.shareVolume)!, 2);
      expect(body.change.grossAmountPercent).toBeCloseTo(percent(grossChange, previous.grossAmount)!, 2);

      // The same figures, on screen.
      const tile = section(page, periodType);
      await expect(tile.locator('h3')).toHaveText(SECTIONS[periodType]);
      await expect(tile.locator('.range')).toHaveText(`${current.startDate} – ${current.endDate}`);
      await expect(tile.locator('.volume')).toContainText(`${number.format(current.shareVolume)} shares`);
      const change = squash(await tile.locator('.change').textContent());
      expect(change).toContain(signed(volumeChange, number.format(volumeChange)));
      expect(change).toContain(`(${signed(volumeChange, percentText.format(percent(volumeChange, previous.shareVolume)!))}%)`);
      const earlier = squash(await tile.locator('.previous').textContent());
      expect(earlier).toBe(`Previous (${previous.startDate} – ${previous.endDate}): ${number.format(previous.shareVolume)} shares`);
      // Direction is shown by class and icon as well as by the sign.
      await expect(tile.locator('.change')).toHaveClass(volumeChange > 0 ? /\bup\b/ : /\bdown\b/);
    }

    // The fixtures were chosen so the two sections show both directions.
    await expect(section(page, 'DAY').locator('.change')).toHaveClass(/\bup\b/);
    await expect(section(page, 'WEEK').locator('.change')).toHaveClass(/\bdown\b/);
  });

  test('AC2: the tile shows when the figures were last refreshed, and Refresh updates it', async ({ page }) => {
    const first = { DAY: insightResponse(page, 'DAY'), WEEK: insightResponse(page, 'WEEK') };
    const before = Date.now();
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const loaded = { DAY: await read(await first.DAY), WEEK: await read(await first.WEEK) };
    const after = Date.now();

    // The server stamps the answer when it computes it, so the stamp lies inside this window
    // (a second of slack for the whole-second resolution of some clocks).
    for (const periodType of ['DAY', 'WEEK'] as const) {
      const stamp = Date.parse(loaded[periodType].generatedAt);
      expect(stamp).toBeGreaterThanOrEqual(before - 1000);
      expect(stamp).toBeLessThanOrEqual(after + 1000);
    }

    // Rendered the way the app does ('medium' date and time, in the browser's zone).
    const shown = (periodType: PeriodType, generatedAt: string) =>
      page.evaluate(
        stamp => new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(stamp)),
        generatedAt).then(text => `Last refreshed ${squash(text)}`);
    for (const periodType of ['DAY', 'WEEK'] as const) {
      await expect.poll(async () => squash(await section(page, periodType).locator('.refreshed').textContent()))
        .toBe(await shown(periodType, loaded[periodType].generatedAt));
    }

    const second = { DAY: insightResponse(page, 'DAY'), WEEK: insightResponse(page, 'WEEK') };
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    const refreshed = { DAY: await read(await second.DAY), WEEK: await read(await second.WEEK) };
    for (const periodType of ['DAY', 'WEEK'] as const) {
      expect(Date.parse(refreshed[periodType].generatedAt)).toBeGreaterThan(Date.parse(loaded[periodType].generatedAt));
      await expect.poll(async () => squash(await section(page, periodType).locator('.refreshed').textContent()))
        .toBe(await shown(periodType, refreshed[periodType].generatedAt));
    }
  });

  test.describe('AC3: access', () => {
    test('an Analyst sees the tile', async ({ page }) => {
      await signIn(page, ANALYST);
      await expect(page).toHaveURL(/\/analyst$/);
      await expect(page.locator(TILE)).toBeVisible();
      await expect(page.locator(TILE).getByRole('heading', { name: 'Trading volume vs previous period' })).toBeVisible();
      await expect(section(page, 'DAY').locator('.volume')).toBeVisible();
      await expect(section(page, 'WEEK').locator('.volume')).toBeVisible();
    });

    test('Trading Ops never gets the tile or the figures', async ({ page }) => {
      const requested: string[] = [];
      page.on('request', r => { if (new URL(r.url()).pathname === INSIGHT) requested.push(r.url()); });

      await signIn(page, TRADING_OPS);
      await expect(page).toHaveURL(/\/trade-search$/);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/access-denied$/);
      await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
      await expect(page.locator(TILE)).toHaveCount(0);

      // Asking for the figures directly, with the Trading Ops session, is refused by the service too.
      const response = await page.request.get(INSIGHT, { params: { periodType: 'WEEK' } });
      expect(response.status(), await response.text()).toBe(403);
      expect(requested.filter(url => !url.includes('periodType=WEEK'))).toEqual([]);
    });

    test('a client is refused on the staff login and gets no figures', async ({ page }) => {
      await signIn(page, CLIENT);
      await expect(page.getByRole('alert')).toContainText('This login is for staff only');
      await expect(page).toHaveURL(/\/login$/);

      const response = await page.request.get(INSIGHT, { params: { periodType: 'DAY' } });
      expect(response.status(), await response.text()).toBe(401);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.locator(TILE)).toHaveCount(0);
    });

    test('a signed-out visitor gets no tile and no figures', async ({ page }) => {
      const response = await page.request.get(INSIGHT, { params: { periodType: 'WEEK' } });
      expect(response.status(), await response.text()).toBe(401);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.locator(TILE)).toHaveCount(0);
    });
  });
});
