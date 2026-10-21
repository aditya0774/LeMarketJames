import { expect, Page, Response, test } from '@playwright/test';
import { Client } from 'pg';
import { connectDatabase } from '../tests/support/database';
import { newUser, registerViaApi, TestUser } from '../tests/support/users';
import { accountIdOf, addDays, Fill, insertFill, removeClient, todayIn } from './support/insight-fixtures';
import { ACCESS_DENIED_HEADING, ANALYST, CLIENT, signIn, TRADING_OPS } from './support/staff';
import { SELF_SIGNED, STAFF_APP_URL, TRADING_APP_URL } from '../tests/support/app-urls';

/**
 * LMKT-48: the active-client trend tile on the Analyst dashboard, end to end (S40-INT). Everything
 * goes through the real staff app and staff-ops-service, without mocks.
 *
 * AC1  the tile shows how many distinct clients traded on each of the last 30 days, from real data
 * AC2  the tile shows when the figures were last refreshed, and a trade made now shows up
 * AC3  an Analyst sees it; a client and Trading Ops are denied
 *
 * Two throwaway clients get fills on chosen days of the window, and just outside it. The expected
 * counts are folded from raw reporting_trades rows in the test (a Set of client ids per day), not
 * taken from the API under test. Other specs add fills while this one runs, so the API is compared
 * with the data source by polling until the two agree, and facts about the fixtures are lower bounds.
 * Only data setup and the oracle use SQL.
 *
 * The empty window ("No trades in this window") is not run here: it depends on the whole shared
 * database having no fills, which other suites make unreliable. The tile's component test covers it.
 */
const TREND = '/api/v1/reports/insights/active-clients';
const INSIGHTS = '/api/v1/reports/insights';
const WINDOW_DAYS = 30;
// Clients register through the trading app; the staff app has no registration route.
const TRADING_APP = TRADING_APP_URL;
const TILE = 'staff-active-client-trend-tile';

interface Trend {
  startDate: string;
  endDate: string;
  days: { date: string; activeClients: number }[];
  lastWeek: { startDate: string; endDate: string; averageActiveClients: number };
  weekBefore: { startDate: string; endDate: string; averageActiveClients: number };
  flag: { flagged: boolean; changePercent: number | null; direction: string; thresholdPercent: number };
  generatedAt: string;
}

interface DayFigures { clients: number; trades: number }

const number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 3 });
const squash = (text: string | null) => (text ?? '').replace(/\s+/g, ' ').trim();

const isTrend = (r: Response) => new URL(r.url()).pathname === TREND;

/** Collects every answer to the trend endpoint, so a test can wait for the nth one. */
function collectTrends(page: Page): Response[] {
  const seen: Response[] = [];
  page.on('response', r => { if (isTrend(r)) seen.push(r); });
  return seen;
}

async function read(response: Response): Promise<Trend> {
  expect(response.status(), await response.text()).toBe(200);
  expect(response.request().method()).toBe('GET');
  return response.json();
}

async function nthTrend(seen: Response[], n: number): Promise<Trend> {
  await expect.poll(() => seen.length, { timeout: 15000 }).toBeGreaterThanOrEqual(n);
  return read(seen[n - 1]);
}

/** The window the tile shows: the 30 days ending today, oldest first. */
const windowOf = (today: string) => Array.from({ length: WINDOW_DAYS }, (_, i) => addDays(today, i - (WINDOW_DAYS - 1)));

/** Independent oracle: fold the raw filled trades of the window, one Set of client ids per day. */
async function expectedFigures(db: Client, timeZone: string, today: string): Promise<Map<string, DayFigures>> {
  const days = windowOf(today);
  const { rows } = await db.query(
    `SELECT to_char((filled_at AT TIME ZONE 'UTC' AT TIME ZONE $3::text)::date, 'YYYY-MM-DD') AS day, client_id
     FROM reporting_trades
     WHERE (filled_at AT TIME ZONE 'UTC' AT TIME ZONE $3::text)::date BETWEEN $1::date AND $2::date`,
    [days[0], today, timeZone]);
  const folded = new Map<string, { clients: Set<number>; trades: number }>(
    days.map(day => [day, { clients: new Set<number>(), trades: 0 }]));
  for (const row of rows) {
    const day = folded.get(row.day)!;
    day.clients.add(row.client_id);
    day.trades++;
  }
  return new Map([...folded].map(([day, f]) => [day, { clients: f.clients.size, trades: f.trades }]));
}

const card = (page: Page) => page.locator(TILE);
const bars = (page: Page) => card(page).locator('rect.bar');

/** Rendered the way the app does ('medium' date and time, in the browser's zone). */
const shown = (page: Page, generatedAt: string) =>
  page.evaluate(
    stamp => new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(stamp)),
    generatedAt).then(text => `Last refreshed ${squash(text)}`);

test.describe('LMKT-48 active-client trend', () => {
  // One set of fixtures serves every test; the tests that add a fill run in order.
  test.describe.configure({ mode: 'serial' });

  let db: Client;
  let timeZone: string;
  let today: string;
  let clientA: TestUser;
  let clientB: TestUser;
  let accountA: number;
  let accountB: number;

  const fill = (day: string): Fill => ({ day, side: 'BUY', quantity: 10, price: 10 });

  /** Polls until the API's days equal the data source's, which settles races with other specs. */
  async function expectApiMatchesDatabase(page: Page): Promise<Map<string, DayFigures>> {
    let expected = new Map<string, DayFigures>();
    // The poll returns the days that differ, so the expectation needs no value known in advance.
    await expect.poll(async () => {
      const response = await page.request.get(TREND);
      expect(response.status(), await response.text()).toBe(200);
      const body: Trend = await response.json();
      expected = await expectedFigures(db, timeZone, today);
      const actual = new Map(body.days.map(d => [d.date, d.activeClients]));
      return [...expected]
        .filter(([day, f]) => actual.get(day) !== f.clients)
        .map(([day, f]) => `${day}: API ${actual.get(day)}, reporting_trades ${f.clients}`);
    }, { message: 'the API counts equal the distinct clients in reporting_trades' }).toEqual([]);
    return expected;
  }

  test.beforeAll(async ({ playwright }) => {
    db = await connectDatabase();
    clientA = newUser('trenda');
    clientB = newUser('trendb');
    const tradingApp = await playwright.request.newContext({ baseURL: TRADING_APP, ...SELF_SIGNED });
    const staffApp = await playwright.request.newContext({ baseURL: STAFF_APP_URL, ...SELF_SIGNED });
    try {
      await registerViaApi(tradingApp, clientA);
      await registerViaApi(tradingApp, clientB);
      // The reports time zone is a setting (C5), so ask the service instead of assuming one.
      const login = await staffApp.post('/api/auth/login', { data: { username: ANALYST.email, password: ANALYST.password } });
      expect(login.status(), await login.text()).toBe(200);
      const ping = await staffApp.get('/api/v1/reports/ping');
      expect(ping.status()).toBe(200);
      timeZone = (await ping.json()).timeZone;

      today = todayIn(timeZone);
      accountA = await accountIdOf(db, clientA.username);
      accountB = await accountIdOf(db, clientB.username);
      // A trades twice today (one client, two trades), the first and a middle day of the window.
      for (const day of [today, today, addDays(today, -1), addDays(today, -10), addDays(today, -(WINDOW_DAYS - 1))]) {
        await insertFill(db, accountA, timeZone, fill(day));
      }
      // B has nothing today, so a trade made during a test adds exactly one client. Its oldest
      // fill is the day just before the window, which the tile must not show.
      for (const day of [addDays(today, -1), addDays(today, -10), addDays(today, -WINDOW_DAYS)]) {
        await insertFill(db, accountB, timeZone, fill(day));
      }
    } catch (error) {
      await removeClient(db, clientA.username);
      await removeClient(db, clientB.username);
      await db.end();
      throw error;
    } finally {
      await tradingApp.dispose();
      await staffApp.dispose();
    }
  });

  test.afterAll(async () => {
    try {
      await removeClient(db, clientA.username);
      await removeClient(db, clientB.username);
    } finally { await db.end(); }
  });

  test('AC1: the tile shows the distinct clients of each of the last 30 days, from real data', async ({ page }) => {
    const seen = collectTrends(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const body = await nthTrend(seen, 1);
    expect(new URL(seen[0].url()).origin).toBe(new URL(page.url()).origin);

    // An allowlist catches new identifiers as well as known personal-data fields.
    expect(Object.keys(body).sort()).toEqual(['days', 'endDate', 'flag', 'generatedAt', 'lastWeek', 'startDate', 'weekBefore']);
    for (const day of body.days) expect(Object.keys(day).sort()).toEqual(['activeClients', 'date']);
    // BR-17: the two weeks the flag compares, and the flag, are counts and percents only.
    for (const week of [body.lastWeek, body.weekBefore]) {
      expect(Object.keys(week).sort()).toEqual(['averageActiveClients', 'endDate', 'startDate']);
    }
    expect(Object.keys(body.flag).sort()).toEqual(['changePercent', 'direction', 'flagged', 'thresholdPercent']);

    // Thirty consecutive days ending today, oldest first, quiet days included.
    expect(body.endDate).toBe(today);
    expect(body.startDate).toBe(addDays(today, -(WINDOW_DAYS - 1)));
    expect(body.days.map(d => d.date)).toEqual(windowOf(today));
    for (const day of body.days) expect(Number.isInteger(day.activeClients) && day.activeClients >= 0).toBe(true);

    // The counts, against the raw trades. Each client counts once a day however often it traded.
    const expected = await expectApiMatchesDatabase(page);
    const count = (day: string) => body.days.find(d => d.date === day)!.activeClients;
    expect(expected.get(today)!.trades, 'A traded twice today').toBeGreaterThan(expected.get(today)!.clients);
    // Fixtures: A and B both traded on today-1 and today-10; A alone today and on the first day.
    expect(count(addDays(today, -1))).toBeGreaterThanOrEqual(2);
    expect(count(addDays(today, -10))).toBeGreaterThanOrEqual(2);
    expect(count(today)).toBeGreaterThanOrEqual(1);
    expect(count(addDays(today, -(WINDOW_DAYS - 1)))).toBeGreaterThanOrEqual(1);
    // B's trade the day before the window is outside it, however busy that day was.
    expect(body.days.map(d => d.date)).not.toContain(addDays(today, -WINDOW_DAYS));

    // The same figures, on screen.
    const tile = card(page);
    await expect(tile.getByRole('heading', { name: 'Active clients, last 30 days' })).toBeVisible();
    await expect(tile.locator('.range')).toHaveText(`${body.startDate} – ${body.endDate}`);
    await expect(bars(page)).toHaveCount(WINDOW_DAYS);
    await expect.poll(() => bars(page).locator('title').allTextContents())
      .toEqual(body.days.map(d => `${d.date}: ${d.activeClients} active clients`));
    const peak = Math.max(...body.days.map(d => d.activeClients));
    await expect(tile.locator('.latest')).toContainText(number.format(count(today)));
    await expect(tile.locator('.peak')).toContainText(`Peak ${number.format(peak)}`);
    await expect(tile.locator('.chart')).toHaveAttribute('role', 'img');
  });

  test('AC2: the tile shows when the figures were last refreshed, and Refresh updates it', async ({ page }) => {
    const seen = collectTrends(page);
    const before = Date.now();
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const first = await nthTrend(seen, 1);
    const after = Date.now();

    // The server stamps the answer when it computes it, so the stamp lies inside this window
    // (a second of slack for the whole-second resolution of some clocks).
    const stamp = Date.parse(first.generatedAt);
    expect(stamp).toBeGreaterThanOrEqual(before - 1000);
    expect(stamp).toBeLessThanOrEqual(after + 1000);
    await expect.poll(async () => squash(await card(page).locator('.refreshed').textContent()))
      .toBe(await shown(page, first.generatedAt));

    await card(page).getByRole('button', { name: 'Refresh', exact: true }).click();
    const second = await nthTrend(seen, 2);
    expect(Date.parse(second.generatedAt)).toBeGreaterThan(stamp);
    await expect.poll(async () => squash(await card(page).locator('.refreshed').textContent()))
      .toBe(await shown(page, second.generatedAt));
  });

  test('AC2: a trade made now shows up after a refresh, and a client counts once however often it trades', async ({ page }) => {
    const seen = collectTrends(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const todayOf = (t: Trend) => t.days.find(d => d.date === today)!.activeClients;
    const first = await nthTrend(seen, 1);
    const clientsBefore = todayOf(first);
    const refresh = () => card(page).getByRole('button', { name: 'Refresh', exact: true }).click();

    // B had no trade today: its first one adds a client.
    await insertFill(db, accountB, timeZone, fill(today));
    await refresh();
    const second = await nthTrend(seen, 2);
    expect(todayOf(second)).toBeGreaterThanOrEqual(clientsBefore + 1);
    await expect(card(page).locator('.latest')).toContainText(number.format(todayOf(second)));
    await expect.poll(() => bars(page).last().locator('title').textContent())
      .toBe(`${today}: ${todayOf(second)} active clients`);

    // Its second one changes no client count, though it is another trade.
    await insertFill(db, accountB, timeZone, fill(today));
    await refresh();
    const third = await nthTrend(seen, 3);
    const expected = await expectApiMatchesDatabase(page);
    expect(expected.get(today)!.trades).toBeGreaterThanOrEqual(4);
    expect(todayOf(third)).toBeGreaterThanOrEqual(todayOf(second));
    expect(expected.get(today)!.clients, 'two clients, four fixture trades').toBeGreaterThanOrEqual(2);
  });

  test.describe('AC3: access', () => {
    test('an Analyst sees the tile', async ({ page }) => {
      await signIn(page, ANALYST);
      await expect(page).toHaveURL(/\/analyst$/);
      await expect(card(page)).toBeVisible();
      await expect(card(page).getByRole('heading', { name: 'Active clients, last 30 days' })).toBeVisible();
      await expect(bars(page)).toHaveCount(WINDOW_DAYS);
    });

    test('Trading Ops never gets the tile or the figures', async ({ page }) => {
      const requested: string[] = [];
      page.on('request', r => { if (new URL(r.url()).pathname.startsWith(INSIGHTS)) requested.push(r.url()); });

      await signIn(page, TRADING_OPS);
      await expect(page).toHaveURL(/\/trade-search$/);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/access-denied$/);
      await expect(page.getByRole('heading', { name: ACCESS_DENIED_HEADING })).toBeVisible();
      await expect(card(page)).toHaveCount(0);

      // Asking for the figures directly, with the Trading Ops session, is refused by the service too.
      const response = await page.request.get(TREND);
      expect(response.status(), await response.text()).toBe(403);
      // The page itself never asked for them.
      expect(requested).toEqual([]);
    });

    test('a client is refused on the staff login and gets no figures', async ({ page }) => {
      await signIn(page, CLIENT);
      await expect(page.getByRole('alert')).toContainText('This login is for staff only');
      await expect(page).toHaveURL(/\/login$/);

      const response = await page.request.get(TREND);
      expect(response.status(), await response.text()).toBe(401);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
      await expect(card(page)).toHaveCount(0);
    });

    test('a signed-out visitor gets no tile and no figures', async ({ page }) => {
      const response = await page.request.get(TREND);
      expect(response.status(), await response.text()).toBe(401);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
      await expect(card(page)).toHaveCount(0);
    });
  });
});
