import { expect, test, type Page } from '@playwright/test';
import { openTab } from './support/nav';
import { loginViaUi, logoutViaUi, newUser, registerViaApi } from './support/users';

const dollars = (text: string | null): number => Number(text?.replace(/[^0-9.-]/g, '') ?? Number.NaN);

async function apiBuyingPower(page: Page): Promise<number> {
  const response = await page.request.get('/api/balance');
  expect(response.status(), await response.text()).toBe(200);
  const body = await response.json();
  expect(body.success).toBe(true);
  return body.balance.buyingPower;
}

/** LMKT-149 AC1-AC5: the browser, trading-service and durable account balance work as one feature. */
test('deposit and withdrawal update every balance view and persist across a new session', async ({ page, request }) => {
  const user = newUser('cash');
  await registerViaApi(request, user);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);

  const buyingPowerCard = page.locator('.stat-card').filter({ hasText: 'Buying power' });
  await expect(buyingPowerCard.locator('.val')).toHaveText('$10,000.00');

  let depositRequest: { amount: number; requestId: string } | undefined;
  page.on('request', (outgoing) => {
    if (outgoing.method() === 'POST' && outgoing.url().endsWith('/api/v1/portfolio/deposits')) {
      depositRequest = outgoing.postDataJSON();
    }
  });

  await buyingPowerCard.getByRole('button', { name: 'Deposit' }).click();
  const dialog = page.getByRole('dialog', { name: 'Move money' });
  await expect(dialog.getByText('Buying power now').locator('..')).toContainText('$10,000.00');
  await dialog.getByRole('button', { name: '$500', exact: true }).click();
  await expect(dialog.getByText('Buying power after').locator('..')).toContainText('$10,500.00');
  await dialog.getByRole('button', { name: 'Deposit $500.00' }).click();

  await expect(dialog.getByRole('status')).toContainText('Your buying power is now $10,500.00.');
  await expect.poll(() => depositRequest).toEqual({ amount: 500, requestId: expect.any(String) });
  expect(depositRequest!.requestId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  await expect(buyingPowerCard.locator('.val')).toHaveText('$10,500.00');

  // Replaying the browser's exact attempt succeeds but does not add the money twice.
  const replay = await page.request.post('/api/v1/portfolio/deposits', { data: depositRequest });
  expect(replay.status(), await replay.text()).toBe(200);
  await expect.poll(() => apiBuyingPower(page)).toBe(10_500);

  await dialog.getByRole('button', { name: 'Done' }).click();
  await openTab(page, 'Market');
  await page.getByLabel('Filter stocks').fill('AAPL');
  await page.getByRole('row', { name: 'Trade AAPL', exact: true }).click();
  const powerAfter = page.locator('.tf-line.faint').filter({ hasText: 'Buying power after' });
  await expect(powerAfter).toBeVisible();
  expect(dollars(await powerAfter.textContent())).toBeLessThan(10_500);

  await page.goto('/dashboard');
  await buyingPowerCard.getByRole('button', { name: 'Deposit' }).click();
  await dialog.getByLabel('Amount').fill('50000.01');
  await dialog.getByRole('button', { name: 'Deposit $50,000.01' }).click();
  await expect(dialog.getByRole('alert')).toHaveText(
    'Enter an amount above $0 with no more than two decimal places, up to $50000.00.',
  );

  await dialog.getByRole('button', { name: 'Withdraw', exact: true }).click();
  await dialog.getByRole('button', { name: '$1,000', exact: true }).click();
  await dialog.getByRole('button', { name: 'Withdraw $1,000.00' }).click();
  await expect(dialog.getByRole('status')).toContainText('Your buying power is now $9,500.00.');
  await expect(buyingPowerCard.locator('.val')).toHaveText('$9,500.00');
  expect(await apiBuyingPower(page)).toBe(9_500);

  await dialog.getByRole('button', { name: 'Done' }).click();
  await page.reload();
  await expect(buyingPowerCard.locator('.val')).toHaveText('$9,500.00');

  await logoutViaUi(page);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(buyingPowerCard.locator('.val')).toHaveText('$9,500.00');
  expect(await apiBuyingPower(page)).toBe(9_500);
});
