import { test, expect } from '@playwright/test';
import { newUser, registerViaApi } from './support/users';

// Run with SIM_RESPECT_MARKET_HOURS=false on the disposable stack (Jenkins sets this).
test('BUY and SELL execute through the gateway and update cash, holdings, and trade history', async ({ request }) => {
  const user = newUser('execute');
  await registerViaApi(request, user);
  const login = await request.post('/api/auth/login', { data: { username: user.email, password: user.password } });
  expect(login.ok()).toBeTruthy();
  const { accountId } = await login.json();
  const instruments = await (await request.get('/api/v1/instruments')).json();
  const instrumentId = instruments.find((instrument: { symbol: string }) => instrument.symbol === 'AAPL').instrumentId;
  const place = async (orderType: 'BUY' | 'SELL') => {
    const response = await request.post('/api/v1/orders', { data: { accountId, instrumentId, orderType, quantity: 1, pricePerUnit: 0.01 } });
    expect(response.status(), await response.text()).toBe(201);
    const order = await response.json();
    await expect.poll(async () => (await (await request.get(`/api/v1/orders/${order.orderId}`)).json()).orderStatus,
      { timeout: 20000 }).toBe('FILLED');
    return (await request.get(`/api/v1/orders/${order.orderId}`)).json();
  };
  const buy = await place('BUY');
  expect(buy.pricePerUnit).toBeGreaterThan(0.01);
  let holdings = await (await request.get('/api/v1/holdings')).json();
  expect(holdings.holdings.find((holding: { symbol: string }) => holding.symbol === 'AAPL').quantity).toBe(1);
  const afterBuy = await (await request.get('/api/v1/profile')).json();
  expect(afterBuy.cashBalance).toBeCloseTo(10000 - Math.round(buy.pricePerUnit * 100) / 100, 6);
  const sell = await place('SELL');
  expect(sell.pricePerUnit).toBeGreaterThan(0.01);
  holdings = await (await request.get('/api/v1/holdings')).json();
  expect(holdings.holdings).toHaveLength(0);
  const afterSell = await (await request.get('/api/v1/profile')).json();
  expect(afterSell.cashBalance).toBeCloseTo(afterBuy.cashBalance + Math.round(sell.pricePerUnit * 100) / 100, 6);
  const trades = await (await request.get(`/api/v1/trades?accountId=${accountId}`)).json();
  expect(trades.map((trade: { side: string }) => trade.side).sort()).toEqual(['BUY', 'SELL']);
});
