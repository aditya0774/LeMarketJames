import { test, expect, APIRequestContext, Browser } from '@playwright/test';
import { Client } from 'pg';
import { signIn, TRADING_OPS } from '../tests-staff/support/staff';
import {
  auditTrail, connectDatabase, lifecycleSteps, settlementPending, settlementReceipts,
} from './support/database';
import { setExecutionControl } from './support/execution-control';
import { getQuote, pinPrice, releasePrice } from './support/market-control';
import { OrderDto, getOrder, instrumentIdOf, loginViaApi, placeOrder, waitForFinalStatus } from './support/orders';
import {
  failSettlementsFor, resendSettlement, restoreSettlementsFor, settlementFailuresOf,
} from './support/settlement-control';
import { newUser, registerViaApi } from './support/users';
import { SELF_SIGNED, STAFF_APP_URL } from './support/app-urls';

/**
 * LMKT-72 (S17, a buy) and LMKT-74 (S18, a sell): settlement on the live stack, of fills the
 * executor really made. Every order here is placed through the trading app, priced by the worker
 * from the feed and settled by trading-service; nothing is inserted.
 *
 * Two of these tests pause the execution worker for the whole stack, so the file runs in the
 * `execution-recovery` project, after the rest of the suite (playwright.config.ts).
 * Run with SIM_RESPECT_MARKET_HOURS=false, like execution.spec.ts. The alerts need Kafka.
 */
// One at a time, overriding the suite's fullyParallel: a paused worker stops every test's orders.
// The longer timeout is the one CI already has: a test here waits for several worker polls.
test.describe.configure({ mode: 'default', timeout: 60_000 });

// Held so every amount is known exactly. Not a stock holdings.spec.ts or execution.spec.ts holds.
const SYMBOL = 'MA';
// What a new client registers with (support/users.ts).
const OPENING_CASH = 10000;
// The staff app, where Trading Ops read their alerts (the same variable staff-api.spec.ts uses).
const STAFF = STAFF_APP_URL;

// The stages after this suite in Jenkins place orders too; never leave the worker paused for them.
test.afterAll(async ({ request }) => {
  await setExecutionControl(request, true, true);
});

/** What settlement changes for a client, as the client's own API calls show it. */
interface Position {
  cash: number;
  /** Shares of SYMBOL; 0 when the account has no such holding. */
  shares: number;
  averageCost: number | null;
  /** The side of every trade in the client's history, sorted. */
  trades: string[];
}

async function positionOf(request: APIRequestContext, accountId: number): Promise<Position> {
  const profile = await (await request.get('/api/v1/profile')).json();
  const holdings = (await (await request.get('/api/v1/holdings')).json()).holdings;
  const holding = holdings.find((held: { symbol: string }) => held.symbol === SYMBOL);
  const trades = await (await request.get(`/api/v1/trades?accountId=${accountId}`)).json();
  return {
    cash: profile.cashBalance,
    shares: holding?.quantity ?? 0,
    averageCost: holding?.averageCost ?? null,
    trades: trades.map((trade: { side: string }) => trade.side).sort(),
  };
}

/**
 * quantity x price in USD, as settlement moves it: whole cents, halves rounded up (C6). Worked in
 * ten-thousandths of a dollar, the scale of a price, so the result is exact.
 */
function amountOf(quantity: number, price: number): number {
  const tenThousandths = quantity * Math.round(price * 10000);
  return Math.floor((tenThousandths + 50) / 100) / 100;
}

/** A new client, signed in on `request`, and the stock they trade. */
async function newClient(request: APIRequestContext, prefix: string) {
  const user = newUser(prefix);
  await registerViaApi(request, user);
  const accountId = await loginViaApi(request, user);
  return { accountId, instrumentId: await instrumentIdOf(request, SYMBOL) };
}

/** Holds SYMBOL where it already is, so the market is not moved for anyone else. */
async function holdPrice(request: APIRequestContext): Promise<number> {
  const price = (await getQuote(request, SYMBOL)).lastPrice;
  await pinPrice(request, SYMBOL, price);
  return price;
}

/** Puts SYMBOL back at the price it had before the test, and lets it move again. */
async function restorePrice(request: APIRequestContext, price: number): Promise<void> {
  await pinPrice(request, SYMBOL, price);
  await releasePrice(request, SYMBOL);
}

async function buyAndFill(request: APIRequestContext, accountId: number, instrumentId: number,
    quantity: number): Promise<OrderDto> {
  const placed = await placeOrder(request, accountId, instrumentId, 'BUY', quantity);
  const order = await waitForFinalStatus(request, placed.orderId);
  expect(order.orderStatus, `rejected: ${order.rejectionReason}`).toBe('FILLED');
  return order;
}

/** AC4: the order ended REJECTED for this reason, and trading-service applied none of it. */
async function expectRefusedAndNotApplied(db: Client, order: OrderDto, reason: string) {
  expect(order.orderStatus).toBe('REJECTED');
  expect(order.rejectionReason).toBe(reason);
  expect(order.filledAt).toBeNull();
  expect(await settlementPending(db, order.orderId)).toBe(false);
  // The refusal is the order's one stored outcome, so a repeated request gets it again (C6).
  expect((await settlementReceipts(db, order.orderId)).map(receipt => receipt.rejection_reason)).toEqual([reason]);
  // Accepted and priced (PENDING, BR-15), then refused: nothing was settled, so nothing is audited as settled.
  const trail = await auditTrail(db, order.orderId);
  expect(lifecycleSteps(trail)).toEqual(['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'PENDING', 'REJECTED']);
  expect(trail[trail.length - 1].details).toEqual({ reason });
}

/**
 * AC4: Trading Operations are alerted. Staff sign in on the staff app's own login page, and the
 * alert is read with that session through the staff app's origin: the staff app has no alerts
 * screen yet, so this is the staff app's answer to a signed-in TRADING_OPS browser (C6).
 */
async function expectTradingOpsAlerted(browser: Browser, order: OrderDto, accountId: number, reason: string) {
  const context = await browser.newContext({ baseURL: STAFF, ...SELF_SIGNED });
  try {
    const page = await context.newPage();
    await signIn(page, TRADING_OPS);
    await expect(page).toHaveURL(/\/trade-search$/);
    // The alert arrives over Kafka after the rejection commits, so it can be a moment behind.
    await expect.poll(async () => {
      const response = await page.request.get('/api/v1/surveillance/alerts');
      expect(response.status(), await response.text()).toBe(200);
      const alerts: Array<{ orderId: number; accountId: number; reason: string; rejectionReason: string }> =
        (await response.json()).alerts;
      return alerts
        .filter(alert => alert.orderId === order.orderId && alert.reason === 'SETTLEMENT_REFUSED')
        .map(alert => ({ accountId: alert.accountId, rejectionReason: alert.rejectionReason }));
    }, { timeout: 20000 }).toEqual([{ accountId, rejectionReason: reason }]);
  } finally {
    await context.close();
  }
}

/**
 * AC2: while every settlement attempt fails part-way, the order waits as PENDING with its fill
 * still to be settled, and the account is exactly as it was.
 *
 * @returns the waiting order, with the price it will be settled at
 */
async function expectUnsettledAndRecoverable(request: APIRequestContext, db: Client, orderId: number,
    accountId: number, before: Position): Promise<OrderDto> {
  // Two failures: the first attempt and at least one of the worker's retries.
  await expect.poll(() => settlementFailuresOf(request, accountId), { timeout: 20000 })
    .toBeGreaterThanOrEqual(2);
  const waiting = await getOrder(request, orderId);
  expect(waiting.orderStatus).toBe('PENDING');
  expect(waiting.rejectionReason).toBeNull();
  expect(waiting.pricePerUnit, 'priced before settlement was attempted').toBeGreaterThan(0);
  expect(await settlementPending(db, orderId)).toBe(true);
  // None of the three changes, and no record that there was a settlement.
  expect(await positionOf(request, accountId)).toEqual(before);
  expect(await settlementReceipts(db, orderId)).toEqual([]);
  expect(lifecycleSteps(await auditTrail(db, orderId))).toEqual(['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'PENDING']);
  return waiting;
}

/** AC3 and AC5: settled and filled exactly once, and audited with what settlement changed. */
async function expectSettledOnce(db: Client, order: OrderDto, cashDelta: number, quantityDelta: number) {
  const receipts = await settlementReceipts(db, order.orderId);
  expect(receipts).toHaveLength(1);
  expect(receipts[0].rejection_reason).toBeNull();
  expect(receipts[0].order_type).toBe(order.orderType);
  expect(Number(receipts[0].quantity)).toBe(order.quantity);
  expect(Number(receipts[0].price_per_unit)).toBe(order.pricePerUnit);
  const trail = await auditTrail(db, order.orderId);
  // One PENDING, one SETTLED and one FILLED, however many attempts there were.
  expect(lifecycleSteps(trail)).toEqual(['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'PENDING', 'SETTLED', 'FILLED']);
  const settled = trail.find(event => event.action === 'SETTLED')!.details;
  expect(Number(settled.cashDelta)).toBeCloseTo(cashDelta, 6);
  expect(Number(settled.quantityDelta)).toBe(quantityDelta);
}

/** AC3: the same fill received again is answered as before and moves nothing a second time. */
async function expectDuplicateFillChangesNothing(request: APIRequestContext, db: Client, order: OrderDto,
    accountId: number, instrumentId: number, settled: Position) {
  const again = await resendSettlement(request, accountId, instrumentId, order);
  expect(again.status(), await again.text()).toBe(200);
  expect((await again.json()).rejectionReason ?? null).toBeNull();
  expect(await positionOf(request, accountId)).toEqual(settled);
  expect(await settlementReceipts(db, order.orderId)).toHaveLength(1);
  const steps = lifecycleSteps(await auditTrail(db, order.orderId));
  expect(steps.filter(step => step === 'SETTLED')).toHaveLength(1);
  expect(steps.filter(step => step === 'FILLED')).toHaveLength(1);
  expect((await getOrder(request, order.orderId)).orderStatus).toBe('FILLED');
}

test.describe('A settlement that would overdraw the account is refused (S17 and S18, AC4)', () => {
  test('S17 AC4: a buy the client can no longer afford at fill time is REJECTED as INSUFFICIENT_CASH, '
      + 'changes nothing, and Trading Ops are alerted', async ({ request, browser }) => {
    const { accountId, instrumentId } = await newClient(request, 'nocash');
    const db = await connectDatabase();
    const price = await holdPrice(request);
    try {
      const before = await positionOf(request, accountId);
      expect(before).toEqual({ cash: OPENING_CASH, shares: 0, averageCost: null, trades: [] });

      // Affordable when it is placed, so it passes the placement check and is saved...
      await setExecutionControl(request, false, true);
      const quantity = Math.floor(before.cash * 0.9 / (await getQuote(request, SYMBOL)).askPrice);
      const placed = await placeOrder(request, accountId, instrumentId, 'BUY', quantity);

      // ...and too dear by the time the worker fills it: the price has risen by a quarter (C4).
      await pinPrice(request, SYMBOL, Math.round(price * 125) / 100);
      expect(quantity * (await getQuote(request, SYMBOL)).askPrice, 'the fill costs more than the cash')
        .toBeGreaterThan(before.cash);
      await setExecutionControl(request, true, true);

      const rejected = await waitForFinalStatus(request, placed.orderId);
      await expectRefusedAndNotApplied(db, rejected, 'INSUFFICIENT_CASH');
      // Cash and holdings unchanged, and no trade recorded.
      expect(await positionOf(request, accountId)).toEqual(before);
      await expectTradingOpsAlerted(browser, rejected, accountId, 'INSUFFICIENT_CASH');
    } finally {
      await setExecutionControl(request, true, true);
      await restorePrice(request, price);
      await db.end();
    }
  });

  test('S18 AC4: a sell of shares that are gone by fill time is REJECTED as INSUFFICIENT_HOLDINGS, '
      + 'changes nothing, and Trading Ops are alerted', async ({ request, browser }) => {
    const { accountId, instrumentId } = await newClient(request, 'noshares');
    const db = await connectDatabase();
    const price = await holdPrice(request);
    try {
      await buyAndFill(request, accountId, instrumentId, 3);
      const held = await positionOf(request, accountId);
      expect(held.shares).toBe(3);

      // Two sells of 2 from a holding of 3. Placement reserves nothing (C6), so both are saved;
      // the worker takes them in the order they were placed.
      await setExecutionControl(request, false, true);
      const first = await placeOrder(request, accountId, instrumentId, 'SELL', 2);
      const second = await placeOrder(request, accountId, instrumentId, 'SELL', 2);
      await setExecutionControl(request, true, true);

      const sold = await waitForFinalStatus(request, first.orderId);
      expect(sold.orderStatus, `rejected: ${sold.rejectionReason}`).toBe('FILLED');
      const rejected = await waitForFinalStatus(request, second.orderId);
      await expectRefusedAndNotApplied(db, rejected, 'INSUFFICIENT_HOLDINGS');

      // Only the first sell was applied (S18 AC1, a holding reduced by Q): 1 share is left, not
      // -1, the cash rose by one sale, and one SELL is in the trade history.
      const after = await positionOf(request, accountId);
      expect(after.shares).toBe(1);
      expect(after.averageCost).toBe(held.averageCost);
      expect(after.cash).toBeCloseTo(held.cash + amountOf(2, sold.pricePerUnit!), 6);
      expect(after.trades).toEqual(['BUY', 'SELL']);
      await expectTradingOpsAlerted(browser, rejected, accountId, 'INSUFFICIENT_HOLDINGS');
    } finally {
      await setExecutionControl(request, true, true);
      await restorePrice(request, price);
      await db.end();
    }
  });
});

/**
 * Failure injection: trading-service's settlement test control (C5) makes this client's
 * settlements fail after their writes have reached the database and before they are committed.
 * It is per account, so no other order on the stack is touched and the worker keeps running.
 */
test.describe('A settlement that fails part-way applies nothing and settles once (S17 and S18, AC1-AC3, AC5)', () => {
  test('S17: a buy whose settlement fails part-way leaves cash and holdings untouched, '
      + 'then settles exactly once after recovery', async ({ request }) => {
    const { accountId, instrumentId } = await newClient(request, 'buyfail');
    const db = await connectDatabase();
    const price = await holdPrice(request);
    try {
      const before = await positionOf(request, accountId);
      let placed: OrderDto;
      let waiting: OrderDto;
      await failSettlementsFor(request, accountId);
      try {
        placed = await placeOrder(request, accountId, instrumentId, 'BUY', 3);
        waiting = await expectUnsettledAndRecoverable(request, db, placed.orderId, accountId, before);
      } finally {
        await restoreSettlementsFor(request, accountId);
      }

      // Recovery is the worker's next poll: nobody resubmits anything.
      const filled = await waitForFinalStatus(request, placed.orderId);
      expect(filled.orderStatus, `rejected: ${filled.rejectionReason}`).toBe('FILLED');
      expect(filled.pricePerUnit, 'settled at the price it was first given').toBe(waiting.pricePerUnit);

      // AC1: cash down by Q x P, a new holding of Q, and the trade in the history.
      const cost = amountOf(3, filled.pricePerUnit!);
      const settled = await positionOf(request, accountId);
      expect(settled.cash).toBeCloseTo(before.cash - cost, 6);
      expect(settled.shares).toBe(3);
      // A new holding costs what was paid for it. The holdings API shows that in cents.
      expect(settled.averageCost).toBeCloseTo(filled.pricePerUnit!, 2);
      expect(settled.trades).toEqual(['BUY']);
      await expectSettledOnce(db, filled, -cost, 3);
      await expectDuplicateFillChangesNothing(request, db, filled, accountId, instrumentId, settled);
    } finally {
      await restorePrice(request, price);
      await db.end();
    }
  });

  test('S18: a sell whose settlement fails part-way leaves cash and the holding untouched, '
      + 'then settles exactly once after recovery', async ({ request }) => {
    const { accountId, instrumentId } = await newClient(request, 'sellfail');
    const db = await connectDatabase();
    const price = await holdPrice(request);
    try {
      await buyAndFill(request, accountId, instrumentId, 3);
      const before = await positionOf(request, accountId);
      expect(before.shares).toBe(3);

      // The whole position: the failed settlement had deleted the holding, the hardest thing to
      // put back, and afterwards it is there again with its average cost.
      let placed: OrderDto;
      let waiting: OrderDto;
      await failSettlementsFor(request, accountId);
      try {
        placed = await placeOrder(request, accountId, instrumentId, 'SELL', 3);
        waiting = await expectUnsettledAndRecoverable(request, db, placed.orderId, accountId, before);
      } finally {
        await restoreSettlementsFor(request, accountId);
      }

      const filled = await waitForFinalStatus(request, placed.orderId);
      expect(filled.orderStatus, `rejected: ${filled.rejectionReason}`).toBe('FILLED');
      expect(filled.pricePerUnit, 'settled at the price it was first given').toBe(waiting.pricePerUnit);

      // AC1: cash up by Q x P, the holding removed at zero, and the trade in the history.
      const proceeds = amountOf(3, filled.pricePerUnit!);
      const settled = await positionOf(request, accountId);
      expect(settled.cash).toBeCloseTo(before.cash + proceeds, 6);
      expect(settled.shares).toBe(0);
      expect(settled.trades).toEqual(['BUY', 'SELL']);
      await expectSettledOnce(db, filled, proceeds, -3);
      // Received again, the sell is not refused for the shares it has already taken.
      await expectDuplicateFillChangesNothing(request, db, filled, accountId, instrumentId, settled);
    } finally {
      await restorePrice(request, price);
      await db.end();
    }
  });
});
