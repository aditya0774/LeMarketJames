import { APIRequestContext, expect, test } from '@playwright/test';
import { Client } from 'pg';
import { connectDatabase } from '../tests/support/database';
import { OrderDto, getOrder, instrumentIdOf, loginViaApi, placeOrder, utc } from '../tests/support/orders';
import { TestUser, newUser, registerViaApi } from '../tests/support/users';
import { SELF_SIGNED, STAFF_APP_URL, TRADING_APP_URL } from '../tests/support/app-urls';

/**
 * LMKT-44: reports must not slow down live trading (BR-16, BRS §9.2).
 *
 * staff-ops-service reads the reporting_trades view on the database that order execution writes
 * to, protected only by its read-side limits (services/staff-ops-service/README.md). This test is
 * the evidence that those limits are enough. It measures how long a client's order takes to
 * reach FILLED, first with orders alone (the baseline) and then while analysts run reports
 * nonstop, and fails if reports make confirmations noticeably slower.
 *
 * "Confirmed" is FILLED, timed from the client's POST to the first read that shows the fill, as
 * a client watching the order would see it. The BRS gives no number for it ("close to
 * immediate"), so the target is relative to the baseline, with an absolute cap on top.
 */

const num = (name: string, fallback: number): number => {
  const value = process.env[name];
  return value === undefined || value === '' ? fallback : Number(value);
};

/**
 * Every threshold and load level, in one place. Each can be overridden per run with the
 * environment variable named next to it, e.g. on a bigger agent or for the negative control.
 */
const LOAD = {
  /** Clients placing orders at the same time, each one order after another. */
  clients: num('LOAD_CLIENTS', 4),
  /** Length of each measured phase. */
  phaseMs: num('LOAD_PHASE_SECONDS', 30) * 1000,
  /** Analysts requesting reports at the same time in the loaded phase; more than the reporting pool holds. */
  analysts: num('LOAD_ANALYSTS', 8),
  /** Filled orders inserted before measuring, so every report scans a real volume of trades. */
  historyRows: num('LOAD_HISTORY_ROWS', 50_000),
  /** p95 under load may be at most this many times the baseline p95... */
  maxRatio: num('LOAD_MAX_RATIO', 1.2),
  /** ...or the baseline plus this much, whichever is larger: execution polls every second, so small baselines are coarse. */
  slackMs: num('LOAD_SLACK_MS', 250),
  /** p95 under load must stay under this, whatever the baseline. */
  capMs: num('LOAD_CAP_MS', 2000),
  /** Reports that must have answered 200 in the loaded phase, so the load was real. */
  minReports: num('LOAD_MIN_REPORTS', 20),
};

/** How often a client checks whether its order has filled. */
const ORDER_POLL_MS = 100;
/** An order still open after this long fails the run instead of hanging it. */
const ORDER_GIVE_UP_MS = 20_000;
/** Lets the analysts' first reports reach the database before orders are timed. */
const REPORT_WARM_UP_MS = 2_000;

/** Ample cash for every order of the run; registration gives only the starting deposit. */
const CASH_TOP_UP = 10_000_000;
const SYMBOL = 'MSFT';

// Clients trade through the trading app, analysts read reports through the staff app, as in production.
const TRADING = TRADING_APP_URL;
const STAFF = STAFF_APP_URL;
const ANALYST: TestUser = { username: 'analyst', email: 'analyst@seed.lemarket.com', password: 'Pass123!' };

interface OrderTiming {
  /** POST until a read shows FILLED: what the client experiences. */
  wallMs: number;
  /** filledAt − submittedAt as stored: the server's own share of it, as a cross-check. */
  serverMs: number;
}

interface ReportTally {
  ok: number;
  failed: number;
  failures: string[];
  latenciesMs: number[];
}

/** Nearest-rank percentile; empty input gives NaN so a missing phase can't pass by accident. */
function percentile(values: number[], p: number): number {
  if (values.length === 0) return Number.NaN;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

function stats(values: number[]) {
  return {
    count: values.length,
    p50: percentile(values, 50),
    p95: percentile(values, 95),
    max: values.length ? Math.max(...values) : Number.NaN,
  };
}

async function accountIdOf(db: Client, user: TestUser): Promise<number> {
  const result = await db.query(`SELECT a.account_id FROM accounts a
    JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1`, [user.username]);
  return result.rows[0].account_id;
}

/**
 * A year of filled trades spread over every instrument, on one account of its own. Inserted
 * directly because placing this many orders through the API would take hours; the view counts
 * any FILLED order, however it got there.
 */
async function insertTradeHistory(db: Client, accountId: number, rows: number): Promise<void> {
  await db.query(`INSERT INTO orders
      (account_id, instrument_id, order_type, quantity, price_per_unit, order_status,
       submitted_at, filled_at, created_at, updated_at)
    SELECT $1, ids[1 + g % array_length(ids, 1)],
           CASE WHEN g % 2 = 0 THEN 'BUY' ELSE 'SELL' END, 1 + g % 10, 100, 'FILLED', t, t, t, t
    FROM generate_series(1, $2::int) AS g,
         LATERAL (SELECT (now() AT TIME ZONE 'UTC') - (g % 365) * interval '1 day'
                         - (g % 86400) * interval '1 second' AS t) AS times,
         (SELECT array_agg(instrument_id) AS ids FROM instruments) AS instrument_ids`,
    [accountId, rows]);
  // Fresh statistics, so the reports are planned for the table as it now is.
  await db.query('ANALYZE orders');
}

/** Places one order and waits for it to fill, timing both. */
async function timeOneOrder(client: APIRequestContext, accountId: number, instrumentId: number): Promise<OrderTiming> {
  const startedAt = Date.now();
  const placed = await placeOrder(client, accountId, instrumentId, 'BUY');
  let order: OrderDto = placed;
  while (order.orderStatus !== 'FILLED') {
    if (order.orderStatus === 'REJECTED') {
      throw new Error(`Order ${order.orderId} was rejected (${order.rejectionReason}); the run needs every order to fill`);
    }
    // DELAYED means the worker is waiting for the exchange to open, which no timeout will outlast.
    if (order.orderStatus === 'DELAYED') {
      throw new Error(`Order ${order.orderId} is DELAYED: run the stack with SIM_RESPECT_MARKET_HOURS=false`);
    }
    if (Date.now() - startedAt > ORDER_GIVE_UP_MS) {
      throw new Error(`Order ${order.orderId} still ${order.orderStatus} after ${ORDER_GIVE_UP_MS} ms`);
    }
    await new Promise(resolve => setTimeout(resolve, ORDER_POLL_MS));
    order = await getOrder(client, placed.orderId);
  }
  return { wallMs: Date.now() - startedAt, serverMs: utc(order.filledAt!) - utc(order.submittedAt) };
}

/** Every client places orders one after another until the phase ends. */
async function runOrders(clients: { context: APIRequestContext; accountId: number }[],
    instrumentId: number, durationMs: number): Promise<OrderTiming[]> {
  const endAt = Date.now() + durationMs;
  const timings: OrderTiming[] = [];
  await Promise.all(clients.map(async ({ context, accountId }) => {
    while (Date.now() < endAt) {
      timings.push(await timeOneOrder(context, accountId, instrumentId));
    }
  }));
  return timings;
}

/**
 * The report requests an analyst's screen makes, each over the whole year of history, so every
 * one aggregates the full volume inserted above.
 */
function reportRequests(): string[] {
  const today = new Date();
  const yearAgo = new Date(today.getTime() - 365 * 24 * 3600 * 1000);
  const day = (date: Date) => date.toISOString().slice(0, 10);
  return [
    `/api/v1/reports/trades?periodType=DAY&from=${day(yearAgo)}&to=${day(today)}`,
    `/api/v1/reports/trades?periodType=WEEK&from=${day(yearAgo)}&to=${day(today)}`,
    `/api/v1/reports/trades-by-stock?startDate=${day(yearAgo)}&endDate=${day(today)}`,
  ];
}

/** Analysts request reports back to back, through the staff app, until told to stop. */
function startReports(staff: APIRequestContext, analysts: number) {
  const tally: ReportTally = { ok: 0, failed: 0, failures: [], latenciesMs: [] };
  const requests = reportRequests();
  let running = true;
  const loops = Array.from({ length: analysts }, async (_, analyst) => {
    for (let i = analyst; running; i++) {
      const path = requests[i % requests.length];
      const startedAt = Date.now();
      try {
        const response = await staff.get(`${STAFF}${path}`, { timeout: 60_000 });
        if (response.ok()) {
          tally.ok++;
          tally.latenciesMs.push(Date.now() - startedAt);
        } else {
          tally.failed++;
          tally.failures.push(`${response.status()} ${path}`);
        }
      } catch (error) {
        tally.failed++;
        tally.failures.push(`${(error as Error).message.split('\n')[0]} ${path}`);
      }
    }
  });
  return {
    tally,
    stop: async () => {
      running = false;
      await Promise.all(loops);
      return tally;
    },
  };
}

test('LMKT-44: order confirmations stay on target while reports run (AC1, AC2)', async ({ playwright, request }, testInfo) => {
  const db = await connectDatabase();
  const contexts: APIRequestContext[] = [];
  const historyUser = newUser('loadhist');
  try {
    // Fixtures: a trade history big enough to make reports work, and clients with cash to trade.
    await registerViaApi(request, historyUser);
    await insertTradeHistory(db, await accountIdOf(db, historyUser), LOAD.historyRows);

    const clients = [];
    for (let i = 0; i < LOAD.clients; i++) {
      // One cookie jar per client, so each places orders as itself.
      const context = await playwright.request.newContext({ baseURL: TRADING, ...SELF_SIGNED });
      contexts.push(context);
      const user = newUser('load');
      await registerViaApi(context, user);
      const accountId = await loginViaApi(context, user);
      await db.query('UPDATE accounts SET cash_balance=$2 WHERE account_id=$1', [accountId, CASH_TOP_UP]);
      clients.push({ context, accountId });
    }
    // The stock list needs a signed-in caller, so it is read as the first client.
    const instrumentId = await instrumentIdOf(clients[0].context, SYMBOL);

    const staff = await playwright.request.newContext(SELF_SIGNED);
    contexts.push(staff);
    const login = await staff.post(`${STAFF}/api/auth/login`, {
      data: { username: ANALYST.email, password: ANALYST.password },
    });
    expect(login.status(), await login.text()).toBe(200);

    // Phase A, the baseline: orders only.
    const baseline = await runOrders(clients, instrumentId, LOAD.phaseMs);

    // Phase B: the same orders while analysts run reports nonstop.
    const reports = startReports(staff, LOAD.analysts);
    await new Promise(resolve => setTimeout(resolve, REPORT_WARM_UP_MS));
    const loaded = await runOrders(clients, instrumentId, LOAD.phaseMs);
    const tally = await reports.stop();

    const baselineStats = stats(baseline.map(t => t.wallMs));
    const loadedStats = stats(loaded.map(t => t.wallMs));
    const allowedMs = Math.min(LOAD.capMs,
      Math.max(baselineStats.p95 * LOAD.maxRatio, baselineStats.p95 + LOAD.slackMs));
    const summary = {
      settings: LOAD,
      baseline: { wallMs: baselineStats, serverMs: stats(baseline.map(t => t.serverMs)) },
      underReports: { wallMs: loadedStats, serverMs: stats(loaded.map(t => t.serverMs)) },
      reports: {
        ok: tally.ok, failed: tally.failed,
        latencyMs: stats(tally.latenciesMs),
        firstFailures: tally.failures.slice(0, 10),
      },
      allowedP95Ms: allowedMs,
    };
    // The evidence: attached to the report in every run, pass or fail.
    console.log(JSON.stringify(summary, null, 2));
    await testInfo.attach('load-summary.json', { body: JSON.stringify(summary, null, 2), contentType: 'application/json' });

    // AC2: reports and orders really ran together.
    expect(baselineStats.count, 'orders placed in the baseline').toBeGreaterThan(0);
    expect(loadedStats.count, 'orders placed under report load').toBeGreaterThan(0);
    expect(tally.ok, `reports answered while orders were timed (failures: ${tally.failures.slice(0, 5).join('; ')})`)
      .toBeGreaterThanOrEqual(LOAD.minReports);

    // AC1: confirmations stay within their normal target.
    expect(loadedStats.p95, `p95 to FILLED under report load vs ${allowedMs} ms allowed (baseline p95 ${baselineStats.p95} ms)`)
      .toBeLessThanOrEqual(allowedMs);
  } finally {
    await Promise.all(contexts.map(context => context.dispose()));
    // Remove only the inserted history: those rows have no audit trail, unlike the orders placed
    // through the API, whose audit records may never be deleted (C2).
    await db.query(`DELETE FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a
      JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1)`, [historyUser.username]);
    await db.end();
  }
});
