import { expect, test } from '@playwright/test';
import { openTab } from './support/nav';
import { loginViaUi, newUser, registerViaApi } from './support/users';

test.describe('Trading', () => {
  test('a user can buy a stock from the market list', async ({ page, request }) => {
    const user = newUser('buy');
    await registerViaApi(request, user);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(/\/dashboard$/);

    // AAPL is one of the seeded tradable stocks (database/schema/009_full_lebron_market.sql).
    await openTab(page, 'Market');
    await page.getByLabel('Filter stocks').fill('AAPL');
    await page.getByRole('row', { name: 'Trade AAPL', exact: true }).click();

    // The row opens the trade page with AAPL chosen.
    await expect(page).toHaveURL(/\/trade\?symbol=AAPL$/);
    await expect(page.getByRole('heading', { name: 'Trade AAPL' })).toBeVisible();
    // The buy button stays disabled until the live quote has loaded; click() waits for it.
    await page.getByRole('button', { name: /^Buy 1 AAPL/ }).click();

    // The order's status depends on the simulator (e.g. submitted or filled), so accept any.
    await expect(page.getByRole('status')).toContainText(/Buy order #\d+ for 1 AAPL is \w+\./, { timeout: 15000 });

    // The new order shows up in the order list.
    await openTab(page, 'Orders');
    const orders = page.locator('section.panel', { has: page.getByRole('heading', { name: 'Orders' }) });
    await expect(orders.getByRole('row').filter({ hasText: 'AAPL' }).first()).toContainText('BUY');
  });
});
