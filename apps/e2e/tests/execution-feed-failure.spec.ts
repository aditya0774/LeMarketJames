import { test, expect } from '@playwright/test';
import { auditTrail, connectDatabase, lifecycleSteps, storedOrder } from './support/database';
import { FeedMode, resetFeed, setFeedMode } from './support/market-control';
import { getOrder, instrumentIdOf, loginViaApi, placeOrder, waitForFinalStatus } from './support/orders';
import { newUser, registerViaApi } from './support/users';

/**
 * LMKT-23 AC2 and AC4 on the live stack: an order that reaches execution while the feed has no
 * usable quote is rejected, and is never filled at the unusable price.
 *
 * These tests switch the whole feed (C4), which would refuse every other test's BUY, so they run
 * in the `feed-failure` project after the rest of the suite (playwright.config.ts).
 * Run with SIM_RESPECT_MARKET_HOURS=false, like execution.spec.ts.
 */
// One at a time, overriding the suite's fullyParallel: each test sets the feed mode it needs.
test.describe.configure({ mode: 'default' });

// The stages after this suite in Jenkins place orders too; never leave the feed broken for them.
test.afterAll(async ({ request }) => {
  await resetFeed(request);
});

const cases: Array<{ mode: FeedMode; reason: string; title: string }> = [
  { mode: 'STALE', reason: 'STALE_QUOTE', title: 'the only quote is stale' },
  { mode: 'UNAVAILABLE', reason: 'PRICE_UNAVAILABLE', title: 'there is no quote' },
];

for (const { mode, reason, title } of cases) {
  test(`AC2, AC4: when ${title}, the accepted order is rejected as ${reason} and not filled`, async ({ request }) => {
    const user = newUser('nofill');
    await registerViaApi(request, user);
    const accountId = await loginViaApi(request, user);
    const instrumentId = await instrumentIdOf(request, 'AAPL');
    const db = await connectDatabase();
    try {
      // A share to sell, bought through real execution while the feed is healthy.
      await resetFeed(request);
      const buy = await waitForFinalStatus(request, (await placeOrder(request, accountId, instrumentId, 'BUY')).orderId);
      expect(buy.orderStatus, `rejected: ${buy.rejectionReason}`).toBe('FILLED');
      const cashBefore = (await (await request.get('/api/v1/profile')).json()).cashBalance;

      // A SELL, because placement prices a BUY and would refuse it before it is ever saved.
      // A SELL is checked against holdings only, so it is saved, accepted, and meets the bad feed
      // at execution, which is the case under test.
      await setFeedMode(request, mode);
      const placed = await placeOrder(request, accountId, instrumentId, 'SELL');
      const rejected = await waitForFinalStatus(request, placed.orderId);

      expect(rejected.orderStatus).toBe('REJECTED');
      expect(rejected.rejectionReason).toBe(reason);
      expect(rejected.acceptedAt, 'accepted before execution rejected it').not.toBeNull();
      // Not filled at the unusable quote: nothing of it reached the order.
      expect(rejected.filledAt).toBeNull();
      expect(rejected.pricePerUnit).toBeNull();
      expect(rejected.quoteSource).toBeNull();
      expect(rejected.quoteTime).toBeNull();
      const row = await storedOrder(db, placed.orderId);
      expect(row).toEqual({
        order_status: 'REJECTED', rejection_reason: reason,
        price_per_unit: null, quote_source: null, quote_time: null, filled_at: null,
      });

      // AC4: the rejection is audited with its reason; no fill and no settlement were recorded.
      const trail = await auditTrail(db, placed.orderId);
      expect(lifecycleSteps(trail)).toEqual(['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'REJECTED']);
      expect(trail[trail.length - 1].details).toEqual({ reason });

      // With quotes back, the rejection is final and nothing was settled: the share and the cash
      // are untouched. Read after the reset because holdings prices come from the same feed.
      await resetFeed(request);
      expect((await getOrder(request, placed.orderId)).orderStatus).toBe('REJECTED');
      const holdings = (await (await request.get('/api/v1/holdings')).json()).holdings;
      expect(holdings.find((holding: { symbol: string }) => holding.symbol === 'AAPL').quantity).toBe(1);
      expect((await (await request.get('/api/v1/profile')).json()).cashBalance).toBe(cashBefore);
      const trades = await (await request.get(`/api/v1/trades?accountId=${accountId}`)).json();
      expect(trades.map((trade: { side: string }) => trade.side)).toEqual(['BUY']);
    } finally {
      await resetFeed(request);
      await db.end();
    }
  });
}
