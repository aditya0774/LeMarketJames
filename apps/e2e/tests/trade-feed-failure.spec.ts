import { expect, test } from '@playwright/test';
import { loginViaUi, newUser, registerViaApi } from './support/users';
import { resetFeed, setFeedMode } from './support/market-control';

// This file belongs to the feed-failure project: changing the feed affects every client.
test.beforeEach(async ({ request }) => {
  await resetFeed(request);
});

// Always restore the shared feed, including when a UI assertion fails.
test.afterEach(async ({ request }) => {
  await resetFeed(request);
});

test.describe('Trading feed recovery', () => {
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

  });
});
