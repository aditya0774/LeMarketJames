import { expect, Page, Response, test } from '@playwright/test';
import { Client } from 'pg';
import { connectDatabase } from '../tests/support/database';
import { newUser, registerViaApi, TestUser } from '../tests/support/users';
import { addDays, Fill, insertFill, removeClient, todayIn } from './support/insight-fixtures';
import { ACCESS_DENIED_HEADING, ANALYST, CLIENT, signIn, TRADING_OPS } from './support/staff';
import { SELF_SIGNED, STAFF_APP_URL, TRADING_APP_URL } from '../tests/support/app-urls';

/**
 * LMKT-46: the volume insight tile on the Analyst dashboard, end to end (S38-INT). Everything
 * goes through the real staff app and staff-ops-service, without mocks.
 *
 * AC1  the tile compares today, this week and last week with the period before, from real data,
 *      and the user can compare two date ranges of their own
 * AC2  the tile shows when the figures were last refreshed, and a trade made now shows up
 * AC3  an Analyst sees it; a client and Trading Ops are denied
 *
 * The seed fills are monthly, so the tile's periods would hold no trades and every figure would be
 * zero. A throwaway client therefore gets fills placed in those periods (the order-history and
 * trade-search specs do the same). The expected figures are folded from reporting_trades in the
 * test, for the exact ranges the tile asked for, not taken from the API under test. Only data
 * setup uses SQL.
 */
const INSIGHT = '/api/v1/reports/insights/volume';
const COMPARE = `${INSIGHT}/compare`;
// Clients register through the trading app; the staff app has no registration route.
const TRADING_APP = TRADING_APP_URL;
const TILE = 'staff-volume-insight-tile';
const PRESETS = { TODAY: 'Today', THIS_WEEK: 'This week', LAST_WEEK: 'Last week' } as const;
type PresetKey = keyof typeof PRESETS;
const PRESET_KEYS = Object.keys(PRESETS) as PresetKey[];

interface Totals { startDate: string; endDate: string; tradeCount: number; shareVolume: number; grossAmount: number }
interface Comparison {
  current: Totals;
  previous: Totals;
  change: { shareVolume: number; shareVolumePercent: number | null; grossAmount: number; grossAmountPercent: number | null };
  generatedAt: string;
}
interface Ranges { currentFrom: string; currentTo: string; previousFrom: string; previousTo: string }

// ---- Calendar: whole days in the reports time zone, as in C6 ----

/** YYYY-MM-DD as the m/d/yyyy a date picker input takes. */
function pickerText(day: string): string {
  const [year, month, date] = day.split('-').map(Number);
  return `${month}/${date}/${year}`;
}

// ---- Fixtures: one throwaway client with fills in the periods the tile compares ----

/** Fills today, yesterday, the day before, and in the last two weeks, so every card has trades. */
function fillsFor(today: string): Fill[] {
  const sinceMonday = (new Date(`${today}T00:00:00Z`).getUTCDay() + 6) % 7;
  const lastMonday = addDays(addDays(today, -sinceMonday), -7);
  return [
    { day: today, side: 'BUY', quantity: 300, price: 100 },
    { day: addDays(today, -1), side: 'BUY', quantity: 2000, price: 100 },
    { day: addDays(today, -1), side: 'SELL', quantity: 1000, price: 150 },
    { day: addDays(today, -2), side: 'BUY', quantity: 1000, price: 200 },
    { day: lastMonday, side: 'BUY', quantity: 1000, price: 120 },
    { day: addDays(lastMonday, -7), side: 'BUY', quantity: 50000, price: 10 },
  ];
}

// ---- Oracle: the same figures, folded from the data source in the test ----

async function expectedTotals(db: Client, timeZone: string, start: string, end: string): Promise<Totals> {
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

const isCompare = (r: Response) => new URL(r.url()).pathname === COMPARE;
const rangesOf = (r: Response): Ranges => {
  const params = new URL(r.url()).searchParams;
  return {
    currentFrom: params.get('currentFrom')!, currentTo: params.get('currentTo')!,
    previousFrom: params.get('previousFrom')!, previousTo: params.get('previousTo')!,
  };
};

/** Collects every answer to the compare endpoint, so a test can wait for the three presets. */
function collectComparisons(page: Page): Response[] {
  const seen: Response[] = [];
  page.on('response', r => { if (isCompare(r)) seen.push(r); });
  return seen;
}

async function read(response: Response): Promise<Comparison> {
  expect(response.status(), await response.text()).toBe(200);
  expect(response.request().method()).toBe('GET');
  return response.json();
}

/** The presets' answers, keyed by card: the tile asks in the order today, this week, last week. */
async function presetAnswers(seen: Response[], from: number): Promise<Record<PresetKey, Response>> {
  await expect.poll(() => seen.length, { timeout: 15000 }).toBeGreaterThanOrEqual(from + 3);
  const batch = seen.slice(from, from + 3);
  // Requests are sent in a fixed order but may be answered in any order, so sort by what was asked.
  const byAsk = [...batch].sort((a, b) => rangesOf(a).currentFrom.localeCompare(rangesOf(b).currentFrom) ||
    rangesOf(a).currentTo.localeCompare(rangesOf(b).currentTo));
  // Today is the one set against the day just before it (on a Monday, this week is one day too).
  const today = batch.find(r => rangesOf(r).previousFrom === addDays(rangesOf(r).currentFrom, -1) &&
    rangesOf(r).currentFrom === rangesOf(r).currentTo)!;
  const rest = byAsk.filter(r => r !== today);
  // Last week starts earlier than this week.
  return { TODAY: today, LAST_WEEK: rest[0], THIS_WEEK: rest[1] };
}

const card = (page: Page, key: string) => page.locator(`${TILE} [data-period="${key}"]`);

async function expectCard(page: Page, db: Client, timeZone: string, key: string, response: Response) {
  const body = await read(response);
  const ranges = rangesOf(response);

  // An allowlist catches new identifiers as well as known personal-data fields.
  expect(Object.keys(body).sort()).toEqual(['change', 'current', 'generatedAt', 'previous']);
  for (const totals of [body.current, body.previous]) {
    expect(Object.keys(totals).sort()).toEqual(['endDate', 'grossAmount', 'shareVolume', 'startDate', 'tradeCount']);
  }
  expect(Object.keys(body.change).sort()).toEqual(['grossAmount', 'grossAmountPercent', 'shareVolume', 'shareVolumePercent']);

  const current = await expectedTotals(db, timeZone, ranges.currentFrom, ranges.currentTo);
  const previous = await expectedTotals(db, timeZone, ranges.previousFrom, ranges.previousTo);
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
  expect(body.change.shareVolumePercent).toBeCloseTo(percent(volumeChange, previous.shareVolume) ?? 0, 2);
  if (previous.shareVolume === 0) expect(body.change.shareVolumePercent).toBeNull();

  // The same figures, on screen.
  const tile = card(page, key);
  await expect(tile.locator('.range')).toHaveText(`${current.startDate} – ${current.endDate}`);
  await expect(tile.locator('.volume')).toContainText(`${number.format(current.shareVolume)} shares`);
  const change = squash(await tile.locator('.change').textContent());
  expect(change).toContain(signed(volumeChange, number.format(volumeChange)));
  const pct = percent(volumeChange, previous.shareVolume);
  expect(change).toContain(pct === null ? 'n/a' : `(${signed(volumeChange, percentText.format(pct))}%)`);
  expect(squash(await tile.locator('.previous').textContent()))
    .toBe(`Compared with ${previous.startDate} – ${previous.endDate}: ${number.format(previous.shareVolume)} shares`);
  // Direction is shown by class and icon as well as by the sign.
  await expect(tile.locator('.change')).toHaveClass(volumeChange > 0 ? /\bup\b/ : volumeChange < 0 ? /\bdown\b/ : /\bflat\b/);
  return { body, current, previous };
}

test.describe('LMKT-46 volume insight', () => {
  // One set of fixtures serves every test; no other spec reads the insight, so nothing races them.
  test.describe.configure({ mode: 'serial' });

  let db: Client;
  let timeZone: string;
  let today: string;
  let accountId: number;
  let client: TestUser;

  test.beforeAll(async ({ playwright }) => {
    db = await connectDatabase();
    client = newUser('volume');
    const tradingApp = await playwright.request.newContext({ baseURL: TRADING_APP, ...SELF_SIGNED });
    const staffApp = await playwright.request.newContext({ baseURL: STAFF_APP_URL, ...SELF_SIGNED });
    try {
      await registerViaApi(tradingApp, client);
      // The reports time zone is a setting (C5), so ask the service instead of assuming one.
      const login = await staffApp.post('/api/auth/login', { data: { username: ANALYST.email, password: ANALYST.password } });
      expect(login.status(), await login.text()).toBe(200);
      const ping = await staffApp.get('/api/v1/reports/ping');
      expect(ping.status()).toBe(200);
      timeZone = (await ping.json()).timeZone;

      today = todayIn(timeZone);
      const { rows } = await db.query(
        'SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1', [client.username]);
      expect(rows).toHaveLength(1);
      accountId = rows[0].account_id;
      for (const fill of fillsFor(today)) await insertFill(db, accountId, timeZone, fill);
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

  test('AC1: the tile compares today, this week and last week with the period before, from real data', async ({ page }) => {
    const seen = collectComparisons(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);

    const answers = await presetAnswers(seen, 0);
    for (const key of PRESET_KEYS) {
      expect(new URL(answers[key].url()).origin).toBe(new URL(page.url()).origin);
      await expect(card(page, key).locator('h3')).toHaveText(PRESETS[key]);
      const { current, previous } = await expectCard(page, db, timeZone, key, answers[key]);
      expect(current.shareVolume, `${key}: the fixtures put trades in the period`).toBeGreaterThan(0);
      if (key !== 'TODAY') expect(previous.shareVolume, `${key}: and in the period before`).toBeGreaterThan(0);
    }

    // Today is one day against the day before; the week so far is set against as many days of last week.
    const ranges = Object.fromEntries(PRESET_KEYS.map(k => [k, rangesOf(answers[k])])) as Record<PresetKey, Ranges>;
    expect(ranges.TODAY.currentFrom).toBe(ranges.TODAY.currentTo);
    expect(ranges.TODAY.previousFrom).toBe(addDays(ranges.TODAY.currentFrom, -1));
    expect(ranges.THIS_WEEK.previousFrom).toBe(addDays(ranges.THIS_WEEK.currentFrom, -7));
    expect(ranges.THIS_WEEK.previousTo).toBe(addDays(ranges.THIS_WEEK.currentTo, -7));
    expect(ranges.LAST_WEEK.currentTo).toBe(addDays(ranges.THIS_WEEK.currentFrom, -1));
    expect(addDays(ranges.LAST_WEEK.currentFrom, 6)).toBe(ranges.LAST_WEEK.currentTo);
  });

  test('AC1: the user can compare two date ranges of their own', async ({ page }) => {
    const seen = collectComparisons(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    await presetAnswers(seen, 0);

    // Yesterday against the day before: both hold fixture trades.
    const wanted: Ranges = {
      currentFrom: addDays(today, -1), currentTo: addDays(today, -1),
      previousFrom: addDays(today, -2), previousTo: addDays(today, -2),
    };
    const form = page.locator(`${TILE} form.custom`);
    await form.getByLabel('Range from').fill(pickerText(wanted.currentFrom));
    await form.getByLabel('Range to').fill(pickerText(wanted.currentTo));
    await form.getByLabel('Compared from').fill(pickerText(wanted.previousFrom));
    await form.getByLabel('Compared to').fill(pickerText(wanted.previousTo));

    const answer = page.waitForResponse(r => isCompare(r) && rangesOf(r).currentFrom === wanted.currentFrom);
    await form.getByRole('button', { name: 'Compare', exact: true }).click();
    const response = await answer;

    expect(rangesOf(response)).toEqual(wanted);
    const { current, previous } = await expectCard(page, db, timeZone, 'CUSTOM', response);
    // Other suites' fills may add to these days, so only the fixtures' share is certain.
    expect(current.shareVolume).toBeGreaterThanOrEqual(3000);
    expect(previous.shareVolume).toBeGreaterThanOrEqual(1000);
  });

  test('AC1: a range that runs backwards is refused with a clear message, not shown as figures', async ({ page }) => {
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const response = await page.request.get(COMPARE, {
      params: { currentFrom: today, currentTo: addDays(today, -1), previousFrom: addDays(today, -3), previousTo: addDays(today, -2) },
    });
    expect(response.status(), await response.text()).toBe(400);
    expect((await response.json()).message).toBeTruthy();

    const missing = await page.request.get(COMPARE);
    expect(missing.status()).toBe(400);
  });

  test('AC1: the daily insight endpoint still compares the last completed day and week', async ({ page }) => {
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    for (const periodType of ['DAY', 'WEEK']) {
      const response = await page.request.get(INSIGHT, { params: { periodType } });
      expect(response.status(), await response.text()).toBe(200);
      const body = await response.json();
      expect(body.periodType).toBe(periodType);
      expect(Object.keys(body).sort()).toEqual(['change', 'current', 'generatedAt', 'inProgress', 'periodType', 'previous']);
      // The running period is reported on its own and never feeds the change.
      expect(body.inProgress.endDate).toBe(today);
      expect(body.change.shareVolume).toBe(body.current.shareVolume - body.previous.shareVolume);
    }
  });

  test('AC2: the tile shows when the figures were last refreshed, and Refresh updates it', async ({ page }) => {
    const seen = collectComparisons(page);
    const before = Date.now();
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const first = await presetAnswers(seen, 0);
    const loaded = Object.fromEntries(await Promise.all(PRESET_KEYS.map(async k => [k, await read(first[k])]))) as Record<PresetKey, Comparison>;
    const after = Date.now();

    // The server stamps the answer when it computes it, so the stamp lies inside this window
    // (a second of slack for the whole-second resolution of some clocks).
    for (const key of PRESET_KEYS) {
      const stamp = Date.parse(loaded[key].generatedAt);
      expect(stamp).toBeGreaterThanOrEqual(before - 1000);
      expect(stamp).toBeLessThanOrEqual(after + 1000);
    }

    // Rendered the way the app does ('medium' date and time, in the browser's zone).
    const shown = (generatedAt: string) =>
      page.evaluate(
        stamp => new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(stamp)),
        generatedAt).then(text => `Last refreshed ${squash(text)}`);
    for (const key of PRESET_KEYS) {
      await expect.poll(async () => squash(await card(page, key).locator('.refreshed').textContent()))
        .toBe(await shown(loaded[key].generatedAt));
    }

    // The dashboard has other tiles with a Refresh button of their own.
    await page.locator(TILE).getByRole('button', { name: 'Refresh', exact: true }).click();
    const second = await presetAnswers(seen, 3);
    for (const key of PRESET_KEYS) {
      const refreshed = await read(second[key]);
      expect(Date.parse(refreshed.generatedAt)).toBeGreaterThan(Date.parse(loaded[key].generatedAt));
      await expect.poll(async () => squash(await card(page, key).locator('.refreshed').textContent()))
        .toBe(await shown(refreshed.generatedAt));
    }
  });

  test('AC2: a trade made now shows up on the Today and This week cards after a refresh', async ({ page }) => {
    const seen = collectComparisons(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const first = await presetAnswers(seen, 0);
    const todayBefore = (await read(first.TODAY)).current.shareVolume;
    const weekBefore = (await read(first.THIS_WEEK)).current.shareVolume;

    await insertFill(db, accountId, timeZone, { day: today, side: 'BUY', quantity: 777, price: 10 });
    await page.locator(TILE).getByRole('button', { name: 'Refresh', exact: true }).click();
    const second = await presetAnswers(seen, 3);

    expect((await read(second.TODAY)).current.shareVolume).toBe(todayBefore + 777);
    expect((await read(second.THIS_WEEK)).current.shareVolume).toBe(weekBefore + 777);
    await expect(card(page, 'TODAY').locator('.volume')).toContainText(`${number.format(todayBefore + 777)} shares`);
  });

  test.describe('AC3: access', () => {
    test('an Analyst sees the tile', async ({ page }) => {
      await signIn(page, ANALYST);
      await expect(page).toHaveURL(/\/analyst$/);
      await expect(page.locator(TILE)).toBeVisible();
      await expect(page.locator(TILE).getByRole('heading', { name: 'Trading volume vs previous period' })).toBeVisible();
      for (const key of PRESET_KEYS) await expect(card(page, key).locator('.volume')).toBeVisible();
    });

    test('Trading Ops never gets the tile or the figures', async ({ page }) => {
      const requested: string[] = [];
      page.on('request', r => { if (new URL(r.url()).pathname.startsWith(INSIGHT)) requested.push(r.url()); });

      await signIn(page, TRADING_OPS);
      await expect(page).toHaveURL(/\/trade-search$/);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/access-denied$/);
      await expect(page.getByRole('heading', { name: ACCESS_DENIED_HEADING })).toBeVisible();
      await expect(page.locator(TILE)).toHaveCount(0);

      // Asking for the figures directly, with the Trading Ops session, is refused by the service too.
      const response = await page.request.get(INSIGHT, { params: { periodType: 'WEEK' } });
      expect(response.status(), await response.text()).toBe(403);
      const range = await page.request.get(COMPARE, {
        params: { currentFrom: today, currentTo: today, previousFrom: addDays(today, -1), previousTo: addDays(today, -1) },
      });
      expect(range.status(), await range.text()).toBe(403);
      // The only requests the page made are the two this test sent itself.
      expect(requested.filter(url => !url.includes('periodType=WEEK') && !url.includes('/compare'))).toEqual([]);
    });

    test('a client is refused on the staff login and gets no figures', async ({ page }) => {
      await signIn(page, CLIENT);
      await expect(page.getByRole('alert')).toContainText('This login is for staff only');
      await expect(page).toHaveURL(/\/login$/);

      const response = await page.request.get(INSIGHT, { params: { periodType: 'DAY' } });
      expect(response.status(), await response.text()).toBe(401);
      const range = await page.request.get(COMPARE, {
        params: { currentFrom: today, currentTo: today, previousFrom: addDays(today, -1), previousTo: addDays(today, -1) },
      });
      expect(range.status(), await range.text()).toBe(401);
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
