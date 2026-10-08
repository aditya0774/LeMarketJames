import { expect, test } from '@playwright/test';
import { instrumentIdOf, loginViaApi, placeOrder } from './support/orders';
import { loginViaUi, newUser, registerViaApi } from './support/users';

const OPS = { email: 'ops@seed.lemarket.com', password: 'Pass123!' };

/**
 * LMKT-77: dashboard uses the real order-status stream. A backend status change must reach
 * the client view quickly enough to feel live.
 */
test('LMKT-77: order status change appears on the dashboard within 2 seconds', async ({
  page,
  request,
  playwright,
}) => {
  const user = newUser('liveord');
  await registerViaApi(request, user);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);

  // Wait for the SSE channel to finish connecting before triggering any status changes.
  await expect(page.locator('app-orders-panel .live-badge')).toHaveText('Live');

  const accountId = await loginViaApi(request, user);
  const instrumentId = await instrumentIdOf(request, 'AAPL');
  const order = await placeOrder(request, accountId, instrumentId, 'BUY');

  const ops = await playwright.request.newContext({
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
  });
  try {
    const login = await ops.post('/api/auth/login', {
      data: { username: OPS.email, password: OPS.password },
    });
    expect(login.status(), await login.text()).toBe(200);

    const reject = await ops.post(`/api/v1/orders/${order.orderId}/reject`, {
      params: { reason: 'REJECTED_BY_OPERATIONS' },
      data: {},
    });
    expect(reject.status(), await reject.text()).toBe(200);
  } finally {
    await ops.dispose();
  }

  const statusPill = page.locator(`tr[data-order-id="${order.orderId}"] td:nth-child(5) .pill`);
  await expect(statusPill).toContainText('Rejected', { timeout: 2000 });
});
