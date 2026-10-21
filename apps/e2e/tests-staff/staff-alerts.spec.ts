import { expect, test } from '@playwright/test';
import { instrumentIdOf, loginViaApi, OrderDto, placeOrder } from '../tests/support/orders';
import { newUser, registerViaApi } from '../tests/support/users';
import { signIn, TRADING_OPS } from './support/staff';
import { SELF_SIGNED, TRADING_APP_URL } from '../tests/support/app-urls';

/**
 * BR-17: Trading Ops' Alerts page, end to end. A throwaway client places a large order through the
 * trading app; orders-service publishes it to Kafka, staff-ops-service's surveillance consumer
 * raises a LARGE_ORDER alert, and Trading Ops find it on the Alerts page, filter by reason and day,
 * and open the order's timeline. Nothing is mocked. Needs Kafka, as in Docker Compose and Jenkins.
 *
 * The order only has to be placed: the alert is raised from the placement event, whatever happens
 * to the order next. Placing a BUY needs a fresh quote, so outside US market hours run with
 * SIM_RESPECT_MARKET_HOURS=false, like the other order specs and Jenkins.
 */
const ALERTS = '/api/v1/surveillance/alerts';

interface Alert { orderId: number; accountId: number; reason: string; occurredAt: string }

test('Trading Ops see a large order on the Alerts page, filter it and open its timeline', async ({ page, playwright }) => {
  await signIn(page, TRADING_OPS);
  await expect(page).toHaveURL(/\/trade-search$/);
  const largeOrderQuantity: number = (await (await page.request.get(ALERTS)).json()).largeOrderQuantity;

  // A client with enough cash for one large order of any stock, whatever prices have drifted to.
  const tradingApp = await playwright.request.newContext({ baseURL: TRADING_APP_URL, ...SELF_SIGNED });
  let order: OrderDto;
  let accountId: number;
  try {
    const quotes: Array<{ askPrice: number }> = await (await tradingApp.get('/api/market/quotes')).json();
    const highestAsk = Math.max(...quotes.map(quote => quote.askPrice));
    const client = newUser('alert');
    await registerViaApi(tradingApp, client, { initialDeposit: Math.max(10000, Math.ceil(highestAsk * largeOrderQuantity * 1.2)) });
    accountId = await loginViaApi(tradingApp, client);
    order = await placeOrder(tradingApp, accountId, await instrumentIdOf(tradingApp, 'AAPL'), 'BUY', largeOrderQuantity);
  } finally {
    await tradingApp.dispose();
  }

  // The alert comes over Kafka, a moment after the order is placed.
  let alert: Alert | undefined;
  await expect.poll(async () => {
    const alerts: Alert[] = (await (await page.request.get(ALERTS, { params: { reason: 'LARGE_ORDER' } })).json()).alerts;
    alert = alerts.find(a => a.orderId === order.orderId);
    return alert?.accountId;
  }, { timeout: 20000 }).toBe(accountId);

  // Trade search links to the Alerts page through the Trading Ops tab bar.
  await page.getByRole('navigation', { name: 'Trading Ops' }).getByRole('link', { name: 'Alerts', exact: true }).click();
  await expect(page).toHaveURL(/\/alerts$/);
  await expect(page.getByRole('heading', { name: 'Alerts', exact: true })).toBeVisible();
  const row = page.locator('tbody tr').filter({ has: page.getByRole('button', { name: `Show the timeline of order ${order.orderId}` }) });
  await expect(row).toHaveCount(1);
  await expect(row.locator('td').nth(1)).toHaveText('Large order');
  await expect(row.locator('td').nth(3)).toHaveText(String(accountId));
  await expect(row.locator('td').nth(6)).toHaveText(String(largeOrderQuantity));

  // Narrowed to its reason and its UTC day it stays; narrowed to the other reason it goes.
  const day = alert!.occurredAt.slice(0, 10);
  await page.getByLabel('Reason').selectOption('LARGE_ORDER');
  await page.getByLabel('Start date (UTC)').fill(day);
  await page.getByLabel('End date (UTC)').fill(day);
  await page.getByRole('button', { name: 'Show alerts' }).click();
  await expect(row).toHaveCount(1);
  await expect(page.locator('tbody tr[data-reason="SETTLEMENT_REFUSED"]')).toHaveCount(0);

  await page.getByLabel('Reason').selectOption('SETTLEMENT_REFUSED');
  await page.getByRole('button', { name: 'Show alerts' }).click();
  await expect(page.locator('.al-status')).not.toContainText('Loading');
  await expect(row).toHaveCount(0);

  // Back to everything, then the order's timeline from the audit trail.
  await page.getByRole('button', { name: 'Clear' }).click();
  await row.getByRole('button', { name: `Show the timeline of order ${order.orderId}` }).click();
  const timeline = page.getByRole('region', { name: 'Order timeline' });
  await expect(timeline.getByRole('heading', { name: `Order timeline #${order.orderId}` })).toBeVisible();
  await expect(timeline.locator('.event-type').first()).toHaveText('SUBMITTED');
});
