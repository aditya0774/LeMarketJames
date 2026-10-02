import { expect, test } from '@playwright/test';
import { loginViaUi, newUser, registerViaApi } from './support/users';
import { setFeedMode } from './support/market-control';

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
    await expect(dialog.getByRole('status')).toHaveText(/Buy order #\d+ for 1 AAPL is \w+\./);
    await dialog.getByRole('button', { name: 'Done' }).click();
    await expect(dialog).toBeHidden();

    // The new order shows up in the dashboard's order list.
    const orders = page.locator('section.panel', { has: page.getByRole('heading', { name: 'Orders' }) });
    await expect(orders.getByRole('row').filter({ hasText: 'AAPL' }).first()).toContainText('BUY');
  });

  test('LMKT-34: User sees banner when feed unavailable and trading is disabled', async ({ page, request }) => {
    const user = newUser('feed-failure');
    await registerViaApi(request, user);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(/\/dashboard$/);

    // AC1: Trigger feed unavailable, open trade dialog, verify banner appears
    await setFeedMode(request, 'UNAVAILABLE');
    await page.getByLabel('Filter stocks').fill('AAPL');
    await page.getByRole('row', { name: 'Trade AAPL', exact: true }).click();

    const dialog = page.getByRole('dialog', { name: 'Trade AAPL' });
    await expect(dialog).toBeVisible();
    
    // AC1: Banner should be visible when feed is unavailable
    const errorBanner = dialog.getByRole('alert').filter({ hasText: 'Prices are temporarily unavailable' });
    await expect(errorBanner).toBeVisible();

    // AC2: Place buy order button should be disabled when feed is unavailable
    const buyButton = dialog.getByRole('button', { name: 'Place buy order' });
    await expect(buyButton).toBeDisabled();

    // AC3: Restore feed to LIVE and verify recovery
    await setFeedMode(request, 'LIVE');
    
    // Wait for the banner to disappear (quote should load and quoteError should clear)
    await expect(errorBanner).not.toBeVisible({ timeout: 10000 });
    
    // AC3: Button should be enabled again once quote loads
    await expect(buyButton).toBeEnabled({ timeout: 10000 });

    // AC4: Verify that the order can now be placed successfully
    await buyButton.click();
    await expect(dialog.getByRole('status')).toHaveText(/Buy order #\d+ for 1 AAPL is \w+\./);
    
    // Cleanup: Close dialog and verify order is in dashboard
    await dialog.getByRole('button', { name: 'Done' }).click();
    const orders = page.locator('section.panel', { has: page.getByRole('heading', { name: 'Orders' }) });
    await expect(orders.getByRole('row').filter({ hasText: 'AAPL' }).first()).toContainText('BUY');

    // Ensure feed is back to LIVE for subsequent tests
    await setFeedMode(request, 'LIVE');
  });
});
