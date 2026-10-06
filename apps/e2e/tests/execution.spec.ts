import { test, expect, APIRequestContext } from '@playwright/test';
import { Client } from 'pg';
import { auditTrail, connectDatabase, lifecycleSteps, ruleChecks, storedOrder } from './support/database';
import { getQuote, pinPrice, releasePrice } from './support/market-control';
import { OrderDto, QUOTE_SOURCE, instrumentIdOf, loginViaApi, placeOrder, utc, waitForFinalStatus } from './support/orders';
import { newUser, registerViaApi } from './support/users';

// Run with SIM_RESPECT_MARKET_HOURS=false on the disposable stack (Jenkins sets this).

async function placeAndFill(request: APIRequestContext, accountId: number, instrumentId: number,
    orderType: 'BUY' | 'SELL'): Promise<OrderDto> {
  const placed = await placeOrder(request, accountId, instrumentId, orderType);
  const order = await waitForFinalStatus(request, placed.orderId);
  expect(order.orderStatus, `rejected: ${order.rejectionReason}`).toBe('FILLED');
  return order;
}

test('BUY and SELL execute through the gateway and update cash, holdings, and trade history', async ({ request }) => {
  const user = newUser('execute');
  await registerViaApi(request, user);
  const accountId = await loginViaApi(request, user);
  const instrumentId = await instrumentIdOf(request, 'AAPL');
  const buy = await placeAndFill(request, accountId, instrumentId, 'BUY');
  expect(buy.pricePerUnit).toBeGreaterThan(0.01);
  let holdings = await (await request.get('/api/v1/holdings')).json();
  expect(holdings.holdings.find((holding: { symbol: string }) => holding.symbol === 'AAPL').quantity).toBe(1);
  const afterBuy = await (await request.get('/api/v1/profile')).json();
  expect(afterBuy.cashBalance).toBeCloseTo(10000 - Math.round(buy.pricePerUnit! * 100) / 100, 6);
  const sell = await placeAndFill(request, accountId, instrumentId, 'SELL');
  expect(sell.pricePerUnit).toBeGreaterThan(0.01);
  holdings = await (await request.get('/api/v1/holdings')).json();
  expect(holdings.holdings).toHaveLength(0);
  const afterSell = await (await request.get('/api/v1/profile')).json();
  expect(afterSell.cashBalance).toBeCloseTo(afterBuy.cashBalance + Math.round(sell.pricePerUnit! * 100) / 100, 6);
  const trades = await (await request.get(`/api/v1/trades?accountId=${accountId}`)).json();
  expect(trades.map((trade: { side: string }) => trade.side).sort()).toEqual(['BUY', 'SELL']);
});

/**
 * LMKT-23 on the live stack: a submitted order goes through the real placement checks, is accepted
 * and executed by the worker, and fills at the feed's quote. The stale and missing quote cases
 * (AC2) switch the whole feed, so they live in execution-feed-failure.spec.ts.
 */
test.describe('Fill an accepted order at the current market price (LMKT-23)', () => {
  // Held so the quote at execution is known exactly. Not one of the stocks holdings.spec.ts pins
  // and releases, which would let the price move under this test.
  const SYMBOL = 'IBM';

  /** AC3: the quote used and the execution time are on the order, in the API and in the table. */
  async function expectQuoteStoredWithFill(db: Client, order: OrderDto, quotePrice: number, pinnedAt: string) {
    // Four decimals is the scale execution rounds a quote to (C6).
    expect(order.pricePerUnit).toBeCloseTo(quotePrice, 4);
    expect(order.quoteSource).toBe(QUOTE_SOURCE);
    expect(order.acceptedAt, 'accepted before it was executed').not.toBeNull();
    expect(order.filledAt).not.toBeNull();
    // A fresh quote: produced after the price was pinned, and no later than the fill it priced.
    const quoteTime = Date.parse(order.quoteTime!);
    expect(quoteTime).toBeGreaterThanOrEqual(Date.parse(pinnedAt));
    expect(quoteTime).toBeLessThanOrEqual(utc(order.filledAt!));

    const row = await storedOrder(db, order.orderId);
    expect(row.order_status).toBe('FILLED');
    expect(Number(row.price_per_unit)).toBe(order.pricePerUnit);
    expect(row.quote_source).toBe(order.quoteSource);
    // The driver keeps milliseconds; the API value carries the feed's full precision.
    expect(Math.abs(row.quote_time!.getTime() - quoteTime)).toBeLessThanOrEqual(1);
    expect(utc(row.filled_at!)).toBe(utc(order.filledAt!));
  }

  /** AC4: one audit event per lifecycle step, settlement before the fill it belongs to (C2). */
  async function expectFillAudited(db: Client, order: OrderDto) {
    const trail = await auditTrail(db, order.orderId);
    expect(lifecycleSteps(trail)).toEqual(['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'SETTLED', 'FILLED']);
    // LMKT-99: an accepted order also has one event per placement check, every one a pass, and
    // they are the checks VALIDATED says ran.
    const checks = ruleChecks(trail);
    expect(checks.length).toBeGreaterThan(0);
    expect(checks.map(check => check.details.result)).toEqual(checks.map(() => 'PASS'));
    expect(trail.find(event => event.action === 'VALIDATED')!.details.checks)
      .toEqual(checks.map(check => check.details.rule));
    const filled = trail[trail.length - 1].details;
    expect(Number(filled.price)).toBe(order.pricePerUnit);
    expect(Number(filled.quantity)).toBe(order.quantity);
  }

  test('AC1, AC3, AC4: a submitted order fills at the fresh quote, which is stored with the fill and audited',
      async ({ request }) => {
    const user = newUser('fill');
    await registerViaApi(request, user);
    const accountId = await loginViaApi(request, user);
    const instrumentId = await instrumentIdOf(request, SYMBOL);
    const db = await connectDatabase();
    try {
      // Hold the price where it already is, so the market is not moved for anyone else.
      await pinPrice(request, SYMBOL, (await getQuote(request, SYMBOL)).lastPrice);
      const quote = await getQuote(request, SYMBOL);
      // The two sides must differ, or the assertions below could not tell ask from bid.
      expect(quote.askPrice).toBeGreaterThan(quote.bidPrice);

      const placed = await placeOrder(request, accountId, instrumentId, 'BUY');
      expect(placed.quoteSource, 'not priced for execution until the worker runs').toBeNull();
      expect(placed.quoteTime).toBeNull();
      const buy = await waitForFinalStatus(request, placed.orderId);
      // AC1: FILLED, and a BUY pays the ask of the quote the feed was serving.
      expect(buy.orderStatus, `rejected: ${buy.rejectionReason}`).toBe('FILLED');
      expect(buy.rejectionReason).toBeNull();
      await expectQuoteStoredWithFill(db, buy, quote.askPrice, quote.lastUpdated);
      await expectFillAudited(db, buy);

      // The same path for the other side: a SELL receives the bid.
      const sell = await waitForFinalStatus(request,
        (await placeOrder(request, accountId, instrumentId, 'SELL')).orderId);
      expect(sell.orderStatus, `rejected: ${sell.rejectionReason}`).toBe('FILLED');
      await expectQuoteStoredWithFill(db, sell, quote.bidPrice, quote.lastUpdated);
      await expectFillAudited(db, sell);
    } finally {
      await releasePrice(request, SYMBOL);
      await db.end();
    }
  });
});
