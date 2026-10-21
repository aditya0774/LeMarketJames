import { test, expect, type APIRequestContext } from '@playwright/test';
import { setExecutionControl } from './support/execution-control';
import { getQuote } from './support/market-control';
import { instrumentIdOf, loginViaApi, placeOrder } from './support/orders';
import { newUser, registerViaApi } from './support/users';

/** Every registerViaApi account starts with this deposit (support/users.ts). */
const DEPOSIT = 10000;

/** Posts an order the way placeOrder does, but returns the response, so a refusal can be read. */
async function tryOrder(request: APIRequestContext, accountId: number, instrumentId: number,
    orderType: 'BUY' | 'SELL', quantity: number) {
  return request.post('/api/v1/orders', { data: { accountId, instrumentId, orderType, quantity } });
}

test.describe('Placement rules (BR-05)', () => {
  // Pausing execution is global, so these never overlap another test that relies on it.
  test.describe.configure({ mode: 'serial' });

  test('an open BUY reserves its cash, so a second BUY cannot spend it again', async ({ request }) => {
    const user = newUser('rsv');
    await registerViaApi(request, user);
    const accountId = await loginViaApi(request, user);
    const instrumentId = await instrumentIdOf(request, 'AAPL');

    try {
      // Keep the first order open: once it fills, the plain balance would refuse the second anyway.
      await setExecutionControl(request, false, true);
      // About 60% of the deposit: one fits, two don't.
      const quantity = Math.floor((DEPOSIT * 0.6) / (await getQuote(request, 'AAPL')).askPrice);
      const first = await placeOrder(request, accountId, instrumentId, 'BUY', quantity);
      const reserved = quantity * first.pricePerUnit!;

      const second = await tryOrder(request, accountId, instrumentId, 'BUY', quantity);
      const body = await second.json();
      expect(second.status(), JSON.stringify(body)).toBe(400);
      expect(body.code).toBe('INSUFFICIENT_CASH');

      // The cash itself hasn't moved; the open order only holds it, as buying power shows.
      const balance = (await (await request.get('/api/balance')).json()).balance;
      expect(balance.cash).toBeCloseTo(DEPOSIT, 2);
      expect(balance.buyingPower).toBeCloseTo(DEPOSIT - reserved, 1);
    } finally {
      await setExecutionControl(request, true, true);
    }
  });

  test('a client in New Jersey cannot trade the non-US companies, but can trade the rest', async ({ request }) => {
    // Registered with the state name the sign-up form writes, so the rule matches real sign-ups.
    const user = newUser('nj');
    await registerViaApi(request, user, { state: 'New Jersey', city: 'Trenton', zipCode: '08608' });
    const accountId = await loginViaApi(request, user);

    const refused = await tryOrder(request, accountId, await instrumentIdOf(request, 'TSM'), 'BUY', 1);
    const body = await refused.json();
    expect(refused.status(), JSON.stringify(body)).toBe(400);
    expect(body.code).toBe('LOCATION_RESTRICTED');

    await placeOrder(request, accountId, await instrumentIdOf(request, 'AAPL'), 'BUY', 1);
  });

  test('the seeded New Jersey persona is refused a restricted stock', async ({ request }) => {
    // Refusal only: an accepted order would spend the persona's seeded cash on every run (C3).
    const accountId = await loginViaApi(request,
      { username: 'seed_restricted', email: 'seed_restricted@seed.lemarket.com', password: 'Pass123!' });
    const refused = await tryOrder(request, accountId, await instrumentIdOf(request, 'ASML'), 'BUY', 1);
    expect((await refused.json()).code).toBe('LOCATION_RESTRICTED');
  });
});
