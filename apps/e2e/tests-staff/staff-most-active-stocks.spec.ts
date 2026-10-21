import { expect, Page, Response, test } from '@playwright/test';
import { Client } from 'pg';
import { connectDatabase } from '../tests/support/database';
import { newUser, registerViaApi, TestUser } from '../tests/support/users';
import { accountIdOf, addDays, Fill, insertFill, removeClient, todayIn } from './support/insight-fixtures';
import { ACCESS_DENIED_HEADING, ANALYST, CLIENT, signIn, TRADING_OPS } from './support/staff';
import { SELF_SIGNED, STAFF_APP_URL, TRADING_APP_URL } from '../tests/support/app-urls';

/**
 * LMKT-47: the most active stocks tile on the Analyst dashboard, end to end (S39-INT). Everything
 * goes through the real staff app, staff gateway and reporting-service, without mocks.
 *
 * AC1  on the Analyst dashboard, the tile shows the 5 most traded stocks of this week by traded value
 * AC2  the tile shows when the figures were last refreshed, and a trade made now shows up
 * AC3  only an Analyst sees it; a signed-out visitor, a client and Trading Ops are denied
 *
 * The seed fills are monthly, so this week may hold no trades at all. A throwaway client therefore
 * gets fills today on three stocks, so the tile always has rows to rank. The expected ranking is
 * folded from reporting_trades in the test, for this week's days in the reports time zone, not
 * taken from the API under test. Other specs add fills while this one runs, so the API is compared
 * with the data source by polling until the two agree, and facts about the fixtures are lower bounds.
 * Only data setup and the oracle use SQL.
 *
 * The empty week ("No filled trades this week.") is not run here: it depends on the whole shared
 * database having no fills this week, which other suites make unreliable. The tile's component
 * test covers it.
 */
const MOST_ACTIVE = '/api/v1/reports/insights/most-active-stocks';
const TOP = 5;
// Clients register through the trading app; the staff gateway has no registration route.
const TRADING_APP = TRADING_APP_URL;
const TILE = 'staff-most-active-stocks-tile';
const HEADING = 'Most active stocks';
const FIXTURE_SYMBOLS = ['AAPL', 'MSFT', 'NVDA'];

interface Stock { rank: number; symbol: string; tradedValue: number; tradeCount: number; shareVolume: number }
/** A stock as the endpoint returns it: with last week's traded value and its BR-17 flag. */
interface RankedStock extends Stock { previousTradedValue: number; flag: { flagged: boolean; changePercent: number | null; direction: string; thresholdPercent: number } }
interface MostActive {
  startDate: string; endDate: string; previousStartDate: string; previousEndDate: string;
  stocks: RankedStock[]; generatedAt: string;
}

const number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 3 });
const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const squash = (text: string | null) => (text ?? '').replace(/\s+/g, ' ').trim();

/** This week's Monday, the first day the tile counts. Weeks run Monday to Sunday (reporting README). */
function mondayOf(day: string): string {
  const sinceMonday = (new Date(`${day}T00:00:00Z`).getUTCDay() + 6) % 7;
  return addDays(day, -sinceMonday);
}

const isMostActive = (r: Response) => new URL(r.url()).pathname === MOST_ACTIVE;

/** Collects every answer to the insight endpoint, so a test can wait for the nth one. */
function collectAnswers(page: Page): Response[] {
  const seen: Response[] = [];
  page.on('response', r => { if (isMostActive(r)) seen.push(r); });
  return seen;
}

async function read(response: Response): Promise<MostActive> {
  expect(response.status(), await response.text()).toBe(200);
  expect(response.request().method()).toBe('GET');
  return response.json();
}

async function nthAnswer(seen: Response[], n: number): Promise<MostActive> {
  await expect.poll(() => seen.length, { timeout: 15000 }).toBeGreaterThanOrEqual(n);
  return read(seen[n - 1]);
}

/**
 * Independent oracle: every stock traded this week, totalled from the raw view by local day and
 * ranked in the test (traded value, highest first, then symbol), not by the backend's query.
 */
async function rankedStocks(db: Client, timeZone: string, today: string): Promise<Stock[]> {
  const { rows } = await db.query(
    `SELECT symbol, COUNT(*)::int AS trades, SUM(quantity)::text AS shares, SUM(gross_amount)::text AS value
     FROM reporting_trades
     WHERE (filled_at AT TIME ZONE 'UTC' AT TIME ZONE $3::text)::date BETWEEN $1::date AND $2::date
     GROUP BY symbol`,
    [mondayOf(today), today, timeZone]);
  return rows
    .map(row => ({ symbol: row.symbol, tradeCount: row.trades, shareVolume: Number(row.shares), tradedValue: Number(row.value) }))
    .sort((a, b) => b.tradedValue - a.tradedValue || (a.symbol < b.symbol ? -1 : a.symbol > b.symbol ? 1 : 0))
    .map((stock, index) => ({ rank: index + 1, ...stock }));
}

/** Where the API's stocks differ from the expected ones; empty when they agree. */
function differences(actual: Stock[], expected: Stock[]): string[] {
  const found: string[] = [];
  if (actual.length !== expected.length) found.push(`API has ${actual.length} stocks, reporting_trades ${expected.length}`);
  expected.forEach((want, i) => {
    const got = actual[i];
    if (!got || got.rank !== want.rank || got.symbol !== want.symbol || got.tradeCount !== want.tradeCount ||
        got.shareVolume !== want.shareVolume || Math.abs(got.tradedValue - want.tradedValue) > 0.005) {
      found.push(`rank ${want.rank}: API ${JSON.stringify(got)}, reporting_trades ${JSON.stringify(want)}`);
    }
  });
  return found;
}

const card = (page: Page) => page.locator(TILE);
const rows = (page: Page) => card(page).locator('tbody tr');

const percent = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });

/** The "vs last week" cell (BR-17): the move, or the flag chip with it when the server flagged it. */
function vsLastWeek({ flag }: RankedStock): string {
  const move = flag.changePercent === null ? (flag.direction === 'FLAT' ? 'no change' : 'new')
    : flag.changePercent > 0 ? `+${percent.format(flag.changePercent)}%`
    : flag.changePercent < 0 ? `−${percent.format(-flag.changePercent)}%` : '0%';
  return flag.flagged ? `${flag.direction === 'DOWN' ? '▼' : '▲'} Flagged ${move}` : move;
}

/** The table as the app renders it: rank, symbol, trades, shares, traded value, vs last week. */
const rowTexts = (stocks: RankedStock[]) => stocks.map(s =>
  [String(s.rank), s.symbol, number.format(s.tradeCount), number.format(s.shareVolume), money.format(s.tradedValue), vsLastWeek(s)]);

async function shownRows(page: Page): Promise<string[][]> {
  const texts: string[][] = [];
  for (const row of await rows(page).all()) {
    texts.push((await row.locator('td, th').allTextContents()).map(squash));
  }
  return texts;
}

/** Rendered the way the app does ('medium' date and time, in the browser's zone). */
const shown = (page: Page, generatedAt: string) =>
  page.evaluate(
    stamp => new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(stamp)),
    generatedAt).then(text => `Last refreshed ${squash(text)}`);

test.describe('LMKT-47 most active stocks', () => {
  // One set of fixtures serves every test; the test that adds a fill runs after the ones that read.
  test.describe.configure({ mode: 'serial' });

  let db: Client;
  let timeZone: string;
  let today: string;
  let client: TestUser;
  let accountId: number;

  /** Polls until the API's top five equal the data source's, which settles races with other specs. */
  async function expectApiMatchesDatabase(page: Page): Promise<Stock[]> {
    let expected: Stock[] = [];
    await expect.poll(async () => {
      const response = await page.request.get(MOST_ACTIVE);
      expect(response.status(), await response.text()).toBe(200);
      const body: MostActive = await response.json();
      expected = (await rankedStocks(db, timeZone, today)).slice(0, TOP);
      return differences(body.stocks, expected);
    }, { message: "the API's top five equal the ranking folded from reporting_trades" }).toEqual([]);
    return expected;
  }

  test.beforeAll(async ({ playwright }) => {
    db = await connectDatabase();
    client = newUser('active');
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
      accountId = await accountIdOf(db, client.username);
      // Today is always in this week, even on a Monday. Three stocks with different values, one of
      // them traded twice and on both sides: traded value counts BUY and SELL alike (C6).
      const fills: Fill[] = [
        { day: today, side: 'BUY', quantity: 10, price: 300, symbol: 'AAPL' },
        { day: today, side: 'SELL', quantity: 5, price: 300, symbol: 'AAPL' },
        { day: today, side: 'BUY', quantity: 20, price: 100, symbol: 'MSFT' },
        { day: today, side: 'BUY', quantity: 4, price: 250, symbol: 'NVDA' },
      ];
      for (const fill of fills) await insertFill(db, accountId, timeZone, fill);
    } catch (error) {
      await removeClient(client.username);
      await db.end();
      throw error;
    } finally {
      await tradingApp.dispose();
      await staffApp.dispose();
    }
  });

  test.afterAll(async () => {
    try { await removeClient(client.username); } finally { await db.end(); }
  });

  test("AC1: the Analyst dashboard shows this week's 5 most traded stocks by traded value, from real data", async ({ page }) => {
    const seen = collectAnswers(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const body = await nthAnswer(seen, 1);
    // The tile calls its own origin, which the staff app forwards to the staff gateway.
    expect(new URL(seen[0].url()).origin).toBe(new URL(page.url()).origin);

    // An allowlist catches new identifiers as well as known personal-data fields.
    expect(Object.keys(body).sort()).toEqual(['endDate', 'generatedAt', 'previousEndDate', 'previousStartDate', 'startDate', 'stocks']);
    for (const stock of body.stocks) {
      expect(Object.keys(stock).sort()).toEqual(['flag', 'previousTradedValue', 'rank', 'shareVolume', 'symbol', 'tradeCount', 'tradedValue']);
      // BR-17: last week's figure and the flag on it are aggregates too.
      expect(Object.keys(stock.flag).sort()).toEqual(['changePercent', 'direction', 'flagged', 'thresholdPercent']);
    }

    // This week: Monday to today, in the reports time zone.
    expect(body.startDate).toBe(mondayOf(today));
    expect(body.endDate).toBe(today);

    // At most five, ranked 1..n by traded value. The fixtures traded three stocks this week.
    expect(body.stocks.length).toBeLessThanOrEqual(TOP);
    expect(body.stocks.length).toBeGreaterThanOrEqual(FIXTURE_SYMBOLS.length);
    expect(body.stocks.map(s => s.rank)).toEqual(body.stocks.map((_, i) => i + 1));
    for (let i = 1; i < body.stocks.length; i++) {
      expect(body.stocks[i].tradedValue).toBeLessThanOrEqual(body.stocks[i - 1].tradedValue);
    }

    // The ranking, against the raw trades.
    const expected = await expectApiMatchesDatabase(page);
    // Fixture stocks inside the top five carry at least the fixtures' trades.
    for (const stock of expected.filter(s => FIXTURE_SYMBOLS.includes(s.symbol))) {
      expect(stock.tradeCount, `${stock.symbol} has the fixture trades`).toBeGreaterThanOrEqual(stock.symbol === 'AAPL' ? 2 : 1);
    }

    // The same figures, on screen, exactly as the tile received them.
    const tile = card(page);
    await expect(tile.getByRole('heading', { name: HEADING })).toBeVisible();
    await expect(tile.locator('.range')).toHaveText(`This week: ${body.startDate} – ${body.endDate}, against the same `
      + `days of last week (${body.previousStartDate} – ${body.previousEndDate})`);
    await expect(rows(page)).toHaveCount(body.stocks.length);
    await expect.poll(() => shownRows(page)).toEqual(rowTexts(body.stocks));
  });

  test('AC2: the tile shows when the figures were last refreshed, and Refresh updates it', async ({ page }) => {
    const seen = collectAnswers(page);
    const before = Date.now();
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const first = await nthAnswer(seen, 1);
    const after = Date.now();

    // The server stamps the answer when it computes it, so the stamp lies inside this window
    // (a second of slack for the whole-second resolution of some clocks).
    const stamp = Date.parse(first.generatedAt);
    expect(stamp).toBeGreaterThanOrEqual(before - 1000);
    expect(stamp).toBeLessThanOrEqual(after + 1000);
    await expect.poll(async () => squash(await card(page).locator('.refreshed').textContent()))
      .toBe(await shown(page, first.generatedAt));

    // The dashboard has other tiles with a Refresh button of their own.
    await card(page).getByRole('button', { name: 'Refresh', exact: true }).click();
    const second = await nthAnswer(seen, 2);
    expect(Date.parse(second.generatedAt)).toBeGreaterThan(stamp);
    await expect.poll(async () => squash(await card(page).locator('.refreshed').textContent()))
      .toBe(await shown(page, second.generatedAt));
  });

  test('AC2: a trade made now shows up after a refresh', async ({ page }) => {
    const seen = collectAnswers(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    const first = await nthAnswer(seen, 1);

    // A trade worth more than this week's leader, so NVDA is certain to come first whatever else
    // traded this week. 100 shares at a price that clears the leader by at least 10,000.
    const leader = first.stocks[0]?.tradedValue ?? 0;
    const trade: Fill = { day: today, side: 'BUY', quantity: 100, price: Math.ceil(leader / 100) + 100, symbol: 'NVDA' };
    const nvdaBefore = (await rankedStocks(db, timeZone, today)).find(s => s.symbol === 'NVDA')!;
    await insertFill(db, accountId, timeZone, trade);

    await card(page).getByRole('button', { name: 'Refresh', exact: true }).click();
    const second = await nthAnswer(seen, 2);
    const top = second.stocks[0];
    expect(top.symbol).toBe('NVDA');
    expect(top.tradeCount).toBeGreaterThanOrEqual(nvdaBefore.tradeCount + 1);
    expect(top.shareVolume).toBeGreaterThanOrEqual(nvdaBefore.shareVolume + trade.quantity);
    // Lower bounds: another spec may trade NVDA meanwhile, never take a trade away.
    expect(top.tradedValue).toBeGreaterThanOrEqual(nvdaBefore.tradedValue + trade.quantity * trade.price - 0.005);
    await expect.poll(() => shownRows(page)).toEqual(rowTexts(second.stocks));

    // And the whole ranking still equals the raw trades.
    await expectApiMatchesDatabase(page);
  });

  test.describe('AC3: access', () => {
    test('an Analyst sees the tile', async ({ page }) => {
      await signIn(page, ANALYST);
      await expect(page).toHaveURL(/\/analyst$/);
      await expect(card(page)).toBeVisible();
      await expect(card(page).getByRole('heading', { name: HEADING })).toBeVisible();
      await expect(rows(page).first()).toBeVisible();
    });

    test('Trading Ops never gets the tile or the figures', async ({ page }) => {
      const requested: string[] = [];
      page.on('request', r => { if (new URL(r.url()).pathname === MOST_ACTIVE) requested.push(r.url()); });

      await signIn(page, TRADING_OPS);
      await expect(page).toHaveURL(/\/trade-search$/);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/access-denied$/);
      await expect(page.getByRole('heading', { name: ACCESS_DENIED_HEADING })).toBeVisible();
      await expect(card(page)).toHaveCount(0);
      // The page itself never asked for the figures.
      expect(requested).toEqual([]);

      // Asking for them directly, with the Trading Ops session, is refused by the service too.
      const response = await page.request.get(MOST_ACTIVE);
      expect(response.status(), await response.text()).toBe(403);
    });

    test('a client is refused on the staff login and gets no figures', async ({ page }) => {
      await signIn(page, CLIENT);
      await expect(page.getByRole('alert')).toContainText('This login is for staff only');
      await expect(page).toHaveURL(/\/login$/);

      const response = await page.request.get(MOST_ACTIVE);
      expect(response.status(), await response.text()).toBe(401);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
      await expect(card(page)).toHaveCount(0);
    });

    test('a signed-out visitor gets no tile and no figures', async ({ page }) => {
      const response = await page.request.get(MOST_ACTIVE);
      expect(response.status(), await response.text()).toBe(401);
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
      await expect(card(page)).toHaveCount(0);
    });
  });
});
