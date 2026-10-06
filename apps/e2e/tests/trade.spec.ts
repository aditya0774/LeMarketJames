import { expect, test } from '@playwright/test';
import { loginViaUi, newUser, registerViaApi } from './support/users';

test.describe('Trading', () => {
  test('a user can buy a stock from the market list', async ({ page, request }) => {
    const user = newUser('buy');
    await registerViaApi(request, user);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(/\/dashboard$/);

    // AAPL is one of the seeded tradable stocks (database/schema/009_full_lebron_market.sql).
    await page.getByLabel('Filter stocks').fill('AAPL');
    await page.getByRole('row', { name: 'Trade AAPL', exact: true }).click();

    const dialog = page.getByRole('dialog', { name: 'Trade AAPL' });
    await expect(dialog).toBeVisible();
    // The buy button stays disabled until the live quote has loaded; click() waits for it.
    await dialog.getByRole('button', { name: 'Place buy order' }).click();

    // The order's status depends on the simulator (e.g. submitted or filled), so accept any.
    await expect(dialog.getByRole('status')).toHaveText(/Buy order #\d+ for 1 AAPL is \w+\./, { timeout: 15000 });
    await dialog.getByRole('button', { name: 'Done' }).click();
    await expect(dialog).toBeHidden();

    // The new order shows up in the dashboard's order list.
    const orders = page.locator('section.panel', { has: page.getByRole('heading', { name: 'Orders' }) });
    await expect(orders.getByRole('row').filter({ hasText: 'AAPL' }).first()).toContainText('BUY');
  });
});
