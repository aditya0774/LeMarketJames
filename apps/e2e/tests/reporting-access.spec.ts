import { APIRequestContext, APIResponse, expect, test } from '@playwright/test';

/**
 * LMKT-138: only analysts may read reports (contracts/C7-roles.md), checked on reporting-service's
 * ping endpoint with the seed logins (contracts/C3-seed-data.md). These tests only log in and
 * read, so they can share the seed logins safely.
 *
 * This suite checks the service's own rule, so it calls reporting-service directly, which needs
 * its own address when the stack isn't on this machine. The way staff really reach it, through
 * the staff gateway, is covered in staff-gateway.spec.ts; the trading gateway has no route to it.
 */
const REPORTING_SERVICE_URL = process.env.E2E_REPORTING_URL ?? 'http://localhost:8086';
const PING = `${REPORTING_SERVICE_URL}/api/v1/reports/ping`;
const SEED_PASSWORD = 'Pass123!';

/**
 * Logs in the way the apps do, then calls ping with the token that login issued. The token is
 * passed on by hand because the login host and reporting-service's host need not be the same.
 */
async function pingAs(request: APIRequestContext, seedName: string): Promise<APIResponse> {
  const login = await request.post('/api/auth/login', {
    data: { username: `${seedName}@seed.lemarket.com`, password: SEED_PASSWORD },
  });
  expect(login.status(), await login.text()).toBe(200);
  const jwt = (await request.storageState()).cookies.find(cookie => cookie.name === 'jwt');
  expect(jwt, 'login sets the jwt cookie').toBeTruthy();
  return request.get(PING, { headers: { Cookie: `jwt=${jwt!.value}` } });
}

test.describe('Report access', () => {
  test('a caller without a token is refused with 401', async ({ request }) => {
    const response = await request.get(PING);
    expect(response.status(), await response.text()).toBe(401);
  });

  for (const [role, seedName] of [['CLIENT', 'seed_active'], ['TRADING_OPS', 'ops'], ['COMPLIANCE', 'compliance']]) {
    test(`${role} is refused with 403`, async ({ request }) => {
      const response = await pingAs(request, seedName);
      expect(response.status(), await response.text()).toBe(403);
    });
  }

  test('ANALYST gets the ping body', async ({ request }) => {
    const response = await pingAs(request, 'analyst');
    expect(response.status(), await response.text()).toBe(200);
    const body = await response.json();
    expect(body).toMatchObject({ success: true, service: 'reporting-service' });
    // An IANA zone ID; which one is a setting (contracts/C5-config.md), so it isn't repeated here.
    expect(() => new Intl.DateTimeFormat('en-US', { timeZone: body.timeZone })).not.toThrow();
  });
});
