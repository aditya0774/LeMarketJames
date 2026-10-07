import { APIRequestContext, expect, test } from '@playwright/test';
import { instrumentIdOf, loginViaApi, placeOrder } from './support/orders';
import { newUser, registerViaApi, TestUser } from './support/users';

/**
 * LMKT-143: the staff gateway is the staff app's only backend entry point. Staff reach reports,
 * trade search and the order timeline through it, clients are refused, and a staff session and
 * a customer session can exist in one browser (contracts/C7-roles.md).
 *
 * Everything is called the way each app calls it: /api on the app's own origin, which forwards
 * to its gateway. The default `request` fixture is the trading app (E2E_BASE_URL); the staff app
 * needs its own address when the stack isn't on this machine. Both must be on the same host,
 * as in every environment, because that is what makes browsers share their cookies.
 */
const STAFF = process.env.E2E_STAFF_BASE_URL ?? 'http://localhost:4201';
const PING = '/api/v1/reports/ping';
const SEARCH = '/api/v1/orders/trades/search';
const timelineOf = (orderId: number) => `/api/v1/orders/${orderId}/timeline`;

const seedLogin = (name: string): TestUser =>
  ({ username: name, email: `${name}@seed.lemarket.com`, password: 'Pass123!' });
const ANALYST = seedLogin('analyst');
const OPERATIONS = seedLogin('ops');

/** Signs in through the staff gateway, leaving the staff session cookie on the context. */
async function staffSignIn(context: APIRequestContext, user: TestUser): Promise<string[]> {
  const response = await context.post(`${STAFF}/api/auth/login`, {
    data: { username: user.email, password: user.password },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).roles;
}

async function cookieNames(context: APIRequestContext): Promise<string[]> {
  return (await context.storageState()).cookies.map(cookie => cookie.name).sort();
}

/** A fresh client with one placed order, set up through the trading gateway as a customer would. */
async function clientWithOrder(request: APIRequestContext): Promise<{ user: TestUser; orderId: number }> {
  const user = newUser('staffgw');
  await registerViaApi(request, user);
  const accountId = await loginViaApi(request, user);
  const order = await placeOrder(request, accountId, await instrumentIdOf(request, 'MSFT'), 'BUY');
  return { user, orderId: order.orderId };
}

// Each test's `request` has a cookie jar of its own, so every test starts signed out of both apps.
test.describe('Staff gateway', () => {
  test('ANALYST reaches the reporting-service ping with a staff session cookie', async ({ request }) => {
    expect(await staffSignIn(request, ANALYST)).toEqual(['ANALYST']);
    // The browser never sees the services' own cookie name on the staff app.
    expect(await cookieNames(request)).toEqual(['staff_jwt']);

    const ping = await request.get(`${STAFF}${PING}`);
    expect(ping.status(), await ping.text()).toBe(200);
    expect(await ping.json()).toMatchObject({ success: true, service: 'reporting-service' });
  });

  test('TRADING_OPS reaches trade search and an order timeline', async ({ request, playwright }) => {
    const { orderId } = await clientWithOrder(request);
    const staff = await playwright.request.newContext();
    try {
      expect(await staffSignIn(staff, OPERATIONS)).toEqual(['TRADING_OPS']);

      // The order may not have filled yet, so only the search itself is checked, not its rows.
      const search = await staff.get(`${STAFF}${SEARCH}`, { params: { orderId: String(orderId) } });
      expect(search.status(), await search.text()).toBe(200);
      expect(Array.isArray(await search.json())).toBe(true);

      const timeline = await staff.get(`${STAFF}${timelineOf(orderId)}`);
      expect(timeline.status(), await timeline.text()).toBe(200);
      // Placement alone writes audit events, so a timeline is never empty.
      expect((await timeline.json()).length).toBeGreaterThan(0);
    } finally {
      await staff.dispose();
    }
  });

  test('a caller with no staff session is refused with 401', async ({ request }) => {
    for (const path of [PING, SEARCH, timelineOf(1), '/api/auth/me']) {
      const response = await request.get(`${STAFF}${path}`);
      expect(response.status(), path).toBe(401);
    }
  });

  test('a CLIENT is refused on every staff route', async ({ request, playwright }) => {
    const { user, orderId } = await clientWithOrder(request);

    // With only a customer session: the browser sends its jwt cookie to the staff app too, and
    // the staff gateway must not accept it.
    expect(await cookieNames(request)).toEqual(['jwt']);
    for (const path of [PING, SEARCH, timelineOf(orderId), '/api/auth/me']) {
      const response = await request.get(`${STAFF}${path}`);
      expect(response.status(), `customer session on ${path}`).toBe(401);
    }

    // Signed in through the staff gateway: still a client, so every service refuses the role,
    // even for the client's own order.
    const staff = await playwright.request.newContext();
    try {
      expect(await staffSignIn(staff, user)).toEqual(['CLIENT']);
      for (const path of [PING, `${SEARCH}?orderId=${orderId}`, timelineOf(orderId)]) {
        const response = await staff.get(`${STAFF}${path}`);
        expect(response.status(), `client on ${path}`).toBe(403);
      }
    } finally {
      await staff.dispose();
    }
  });

  test('routes nothing but the staff paths', async ({ request }) => {
    await staffSignIn(request, OPERATIONS);
    // Customer features and the rest of the orders API have no route here, whoever asks.
    for (const path of ['/api/v1/instruments', '/api/v1/orders/1', '/api/v1/holdings', '/api/quotes/MSFT']) {
      const response = await request.get(`${STAFF}${path}`);
      expect(response.status(), path).toBe(404);
    }
    const register = await request.post(`${STAFF}/api/auth/register`, { data: {} });
    expect(register.status(), 'staff logins are not self-service').toBe(404);
  });

  test('the trading gateway does not serve reports, even to an analyst', async ({ request }) => {
    // The analyst signs in to the trading app here, so this is the trading gateway's own answer.
    const login = await request.post('/api/auth/login', {
      data: { username: ANALYST.email, password: ANALYST.password },
    });
    expect(login.status(), await login.text()).toBe(200);

    const ping = await request.get(PING);
    expect(ping.status(), await ping.text()).not.toBe(200);
    expect(await ping.text()).not.toContain('reporting-service');
  });

  test('a customer session and a staff session exist at the same time', async ({ request }) => {
    const client = newUser('twosess');
    await registerViaApi(request, client);

    // One cookie jar for both apps, as in a browser with a tab on each.
    await loginViaApi(request, client);
    await staffSignIn(request, OPERATIONS);
    expect(await cookieNames(request)).toEqual(['jwt', 'staff_jwt']);

    const whoAmI = async (origin: string) => {
      const response = await request.get(`${origin}/api/auth/me`);
      return response.status() === 200 ? (await response.json()).roles : response.status();
    };
    expect(await whoAmI('')).toEqual(['CLIENT']);
    expect(await whoAmI(STAFF)).toEqual(['TRADING_OPS']);

    // Each session still does its own work while the other is open.
    expect((await request.get('/api/v1/instruments')).status()).toBe(200);
    expect((await request.get(`${STAFF}${SEARCH}`, { params: { orderId: '1' } })).status()).toBe(200);

    // Signing out of the staff app leaves the customer signed in...
    expect((await request.post(`${STAFF}/api/auth/logout`)).status()).toBe(200);
    expect(await whoAmI(STAFF)).toBe(401);
    expect(await whoAmI('')).toEqual(['CLIENT']);

    // ...and signing out of the trading app leaves staff signed in.
    await staffSignIn(request, OPERATIONS);
    expect((await request.post('/api/auth/logout')).status()).toBe(200);
    expect(await whoAmI('')).toBe(401);
    expect(await whoAmI(STAFF)).toEqual(['TRADING_OPS']);
  });
});
