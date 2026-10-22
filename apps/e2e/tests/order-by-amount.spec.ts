import { test, expect, type APIRequestContext } from '@playwright/test';
import { getQuote } from './support/market-control';
import { instrumentIdOf, loginViaApi, waitForFinalStatus } from './support/orders';
import { loginViaUi, newUser, registerViaApi } from './support/users';

// Run with SIM_RESPECT_MARKET_HOURS=false on the disposable stack (Jenkins sets this), so orders fill.

/** Every registerViaApi account starts with this deposit (support/users.ts). */
const DEPOSIT = 10000;
const SYMBOL = 'AAPL';

/**
 * What settlement moves for a fill, in cents: quantity × price rounded half up to the cent
 * (trading-service HoldingsSettlementService). Worked in whole ten-thousandths of a dollar, the
 * scale prices are stored at, so floating point can't move a half cent.
 */
function settledCents(quantity: number, price: number): number {
  const tenThousandths = Math.round(price * 10000) * quantity;
  return Math.floor((tenThousandths + 50) / 100);
}

async function cashCents(request: APIRequestContext): Promise<number> {
  const profile = await (await request.get('/api/v1/profile')).json();
  return Math.round(profile.cashBalance * 100);
}

async function sharesHeld(request: APIRequestContext): Promise<number> {
  const holdings = (await (await request.get('/api/v1/holdings')).json()).holdings;
  return holdings.find((holding: { symbol: string }) => holding.symbol === SYMBOL)?.quantity ?? 0;
}

/** An amount covering two and a half shares at `price`: two whole shares, whatever the next tick. */
function twoAndAHalfShares(price: number): number {
  return Math.round(price * 2.5 * 100) / 100;
}

test.describe('Orders by amount (BR-04)', () => {
  test('a client buys an amount of AAPL as whole shares, and cash and holdings reconcile to the cent',
      async ({ page, request }) => {
    const user = newUser('amt');
    await registerViaApi(request, user);
    await loginViaApi(request, user);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(/\/dashboard$/);

    const amount = twoAndAHalfShares((await getQuote(request, SYMBOL)).askPrice);
    await page.goto(`/trade?symbol=${SYMBOL}`);
    await expect(page.getByRole('heading', { name: `Trade ${SYMBOL}` })).toBeVisible();

    // Shares is the default; the client switches to Amount and types how much to spend.
    await expect(page.getByLabel('Shares', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Amount', exact: true }).click();
    await page.getByLabel('Amount (USD)').fill(amount.toFixed(2));

    // The ticket says what the amount buys before the client confirms.
    await expect(page.locator('#trade-amount-shares')).toContainText(/Buys 2 shares, about \$[\d,.]+ left over\./);
    await page.getByRole('button', { name: new RegExp(`^Buy 2 ${SYMBOL} · \\$`) }).click();

    const status = page.getByRole('status');
    await expect(status).toContainText(new RegExp(`Buy order #\\d+ for 2 ${SYMBOL} is \\w+\\.`), { timeout: 15000 });
    const orderId = Number(/#(\d+)/.exec((await status.textContent())!)![1]);

    const buy = await waitForFinalStatus(request, orderId);
    expect(buy.orderStatus, `rejected: ${buy.rejectionReason}`).toBe('FILLED');
    expect(buy.quantity).toBe(2);
    expect(buy.requestedAmount).toBe(amount);

    expect(await sharesHeld(request)).toBe(2);
    expect(await cashCents(request)).toBe(DEPOSIT * 100 - settledCents(2, buy.pricePerUnit!));
  });

  test('a sell by amount sells the whole shares it covers at the bid, and the API refuses part shares',
      async ({ request }) => {
    const user = newUser('amts');
    await registerViaApi(request, user);
    const accountId = await loginViaApi(request, user);
    const instrumentId = await instrumentIdOf(request, SYMBOL);
    const order = (size: Record<string, number>, orderType: 'BUY' | 'SELL' = 'BUY') =>
      request.post('/api/v1/orders', { data: { accountId, instrumentId, orderType, ...size } });

    // Whole shares only, whichever way the order is sized, and the size is given exactly once.
    const half = await order({ quantity: 0.5 });
    expect(half.status()).toBe(400);
    expect((await half.json()).code).toBe('INVALID_QUANTITY');
    const tooLittle = await order({ amount: 1 });
    expect(tooLittle.status()).toBe(400);
    expect(await tooLittle.json()).toMatchObject({
      code: 'INVALID_QUANTITY', reason: 'Amount is less than the price of one share',
    });
    const both = await order({ quantity: 1, amount: 500 });
    expect(both.status()).toBe(400);
    expect((await both.json()).errors.quantityOrAmount).toBeTruthy();

    const placedBuy = await order({ quantity: 3 });
    expect(placedBuy.status(), await placedBuy.text()).toBe(201);
    const buy = await waitForFinalStatus(request, (await placedBuy.json()).orderId);
    expect(buy.orderStatus, `rejected: ${buy.rejectionReason}`).toBe('FILLED');

    // Two and a half shares' worth at the bid sells two of the three.
    const placedSell = await order({ amount: twoAndAHalfShares((await getQuote(request, SYMBOL)).bidPrice) }, 'SELL');
    expect(placedSell.status(), await placedSell.text()).toBe(201);
    expect((await placedSell.json()).quantity).toBe(2);
    const sell = await waitForFinalStatus(request, (await placedSell.json()).orderId);
    expect(sell.orderStatus, `rejected: ${sell.rejectionReason}`).toBe('FILLED');

    expect(await sharesHeld(request)).toBe(1);
    expect(await cashCents(request))
      .toBe(DEPOSIT * 100 - settledCents(3, buy.pricePerUnit!) + settledCents(2, sell.pricePerUnit!));
  });
});
