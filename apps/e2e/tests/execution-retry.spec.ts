import { test, expect } from '@playwright/test';
import { auditTrail, connectDatabase, lifecycleSteps, storedOrder } from './support/database';
import { setExecutionControl } from './support/execution-control';
import { instrumentIdOf, loginViaApi, placeOrder, getOrder } from './support/orders';
import { newUser, registerViaApi } from './support/users';

test.describe('Execution recovery (LMKT-24)', () => {
  test.describe.configure({ mode: 'serial' });

  test('keeps an order pending through outage, recovery, and worker restart', async ({ request }) => {
    const user = newUser('retry');
    await registerViaApi(request, user);
    const accountId = await loginViaApi(request, user);
    const instrumentId = await instrumentIdOf(request, 'AAPL');
    const db = await connectDatabase();
    let orderId: number | undefined;

    try {
      // Stop automatic execution before placing the order so the test controls the first attempt.
      await setExecutionControl(request, false, true);
      const placed = await placeOrder(request, accountId, instrumentId, 'BUY');
      orderId = placed.orderId;

      // The real MarketOrderExecutor runs, but settlement is unavailable. The order must remain
      // visible as PENDING rather than becoming a rejection.
      await setExecutionControl(request, true, false);
      await expect.poll(async () => (await getOrder(request, orderId!)).orderStatus)
        .toBe('PENDING');
      const pending = await getOrder(request, orderId);
      expect(pending.rejectionReason).toBeNull();
      expect(pending.pricePerUnit).toBeGreaterThan(0);
      expect(pending.quoteSource).toBe('SIMULATED_FEED');

      // Restore settlement and let the scheduler retry the persisted execution intent.
      await setExecutionControl(request, true, true);
      await expect.poll(async () => (await getOrder(request, orderId!)).orderStatus,
        { timeout: 20000 }).toBe('FILLED');

      // Disable and re-enable the worker while the same persisted intent is available. A fresh
      // execution cycle must still produce at most one fill and one settlement.
      await setExecutionControl(request, false, true);
      const filled = await getOrder(request, orderId);
      expect(filled.orderStatus).toBe('FILLED');
      await setExecutionControl(request, true, true);
      await new Promise(resolve => setTimeout(resolve, 1500));
      expect((await getOrder(request, orderId)).orderStatus).toBe('FILLED');

      const row = await storedOrder(db, orderId);
      expect(row.order_status).toBe('FILLED');
      expect(row.rejection_reason).toBeNull();
      const trail = await auditTrail(db, orderId);
      expect(lifecycleSteps(trail)).toEqual(['SUBMITTED', 'VALIDATED', 'ACCEPTED', 'SETTLED', 'FILLED']);
      expect(trail.filter(event => event.action === 'FILLED')).toHaveLength(1);
      expect(trail.filter(event => event.action === 'SETTLED')).toHaveLength(1);
    } finally {
      await setExecutionControl(request, true, true);
      await db.end();
    }
  });
});
