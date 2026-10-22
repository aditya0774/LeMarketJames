import { expect, test } from '@playwright/test';
import { openTab } from './support/nav';
import { instrumentIdOf, loginViaApi, placeOrder } from './support/orders';
import { resetFeed } from './support/market-control';
import { loginViaUi, newUser, registerViaApi } from './support/users';
import { SELF_SIGNED, TRADING_APP_URL } from './support/app-urls';

const OPS = { email: 'ops@seed.lemarket.com', password: 'Pass123!' };

async function registerFreshUser(request: import('@playwright/test').APIRequestContext) {
  let lastError: unknown;
  for (let attempt = 0; attempt < 5; attempt++) {
    const user = newUser('liveord');
    try {
      await registerViaApi(request, user);
      return user;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

async function rejectOpenOrder(
  client: import('@playwright/test').APIRequestContext,
  ops: import('@playwright/test').APIRequestContext,
  accountId: number,
  instrumentId: number,
): Promise<number> {
  // The execution worker can occasionally finalize an order before operations acts on it.
  // Retry with a fresh order until one is rejected through the operations endpoint.
  for (let attempt = 0; attempt < 5; attempt++) {
    const order = await placeOrder(client, accountId, instrumentId, 'BUY');
    const reject = await ops.post(`/api/v1/orders/${order.orderId}/reject`, {
      params: { reason: 'REJECTED_BY_OPERATIONS' },
      data: {},
    });
    if (reject.status() === 200) {
      return order.orderId;
    }
    if (reject.status() !== 409) {
      expect(reject.status(), await reject.text()).toBe(200);
    }
  }
  throw new Error('Could not reject an open order before execution finalized it.');
}

/**
 * LMKT-77: dashboard uses the real order-status stream. A backend status change must reach
 * the client view quickly enough to feel live.
 */
test('LMKT-77: order status change appears on the dashboard within 2 seconds', async ({
  page,
  request,
  playwright,
}) => {
  const user = await registerFreshUser(request);

  const streamRequestPromise = page.waitForRequest((networkRequest) => {
    const url = new URL(networkRequest.url());
    return url.pathname.endsWith('/api/v1/orders/stream');
  });

  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);

  const streamRequest = await streamRequestPromise;
  expect(streamRequest.headers()['accept']).toContain('text/event-stream');
  // The order rows live on the Orders tab, which keeps its own stream open.
  await openTab(page, 'Orders');

  const accountId = await loginViaApi(request, user);
  const instrumentId = await instrumentIdOf(request, 'AAPL');

  // This scenario needs a placeable order; ensure the shared feed is back to normal first.
  await resetFeed(request);

  const ops = await playwright.request.newContext({
    baseURL: TRADING_APP_URL,
    ...SELF_SIGNED,
  });
  let orderId = 0;
  try {
    const login = await ops.post('/api/auth/login', {
      data: { username: OPS.email, password: OPS.password },
    });
    expect(login.status(), await login.text()).toBe(200);

    orderId = await rejectOpenOrder(request, ops, accountId, instrumentId);
  } finally {
    await ops.dispose();
  }

  const statusPill = page.locator(`tr[data-order-id="${orderId}"] td:nth-child(5) .pill`);
  await expect(statusPill).toContainText('Rejected', { timeout: 2000 });
});
