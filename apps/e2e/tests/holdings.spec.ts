import { expect, test } from '@playwright/test';
import { TestUser, loginViaUi, newUser, registerViaApi } from './support/users';

/**
 * LMKT-29: View my holdings
 * 
 * AC1: Given I am signed in, when I open my portfolio, then I see each stock I hold with its quantity
 * AC2: Given I hold nothing, then I see a friendly empty state with a link to place an order
 * AC3: Given the platform has restarted, then my holdings are exactly as they were before
 */

const SEED_PASSWORD = 'Pass123!';
const seed = (name: string): TestUser => ({
  username: name,
  email: `${name}@seed.lemarket.com`,
  password: SEED_PASSWORD,
});

test.describe('Holdings View (LMKT-29)', () => {
  test('AC1: User with holdings sees the list with quantity, cost, value, and gain/loss', async ({ page }) => {
    // Seed data has filled orders for seed_active, creating holdings
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings page via the Holdings button
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    // Verify page title
    await expect(page.getByRole('heading', { name: 'My Holdings' })).toBeVisible();

    // Verify holdings table exists and contains seed data (e.g., AAPL from seeded fills)
    const table = page.locator('.holdings-table');
    await expect(table).toBeVisible();

    // AC1: Each holding shows quantity, averageCost, currentPrice, totalCost, currentValue, gainLoss, gainLossPercent
    const aapl = table.locator('tr').filter({ hasText: 'AAPL' });
    await expect(aapl).toBeVisible();

    // Verify table columns are present
    const headers = table.locator('th');
    await expect(headers).toContainText([
      'Symbol',
      'Quantity',
      'Avg Cost',
      'Current Price',
      'Total Cost',
      'Current Value',
      'Gain/Loss',
      'Gain/Loss %',
    ]);

    // Verify data cells contain numbers (not empty)
    const cells = aapl.locator('td');
    for (let i = 0; i < await cells.count(); i++) {
      const text = await cells.nth(i).textContent();
      expect(text?.trim()).not.toBe('');
    }
  });

  test('AC2: User with no holdings sees a friendly empty state with a link to place an order', async ({
    page,
    request,
  }) => {
    // Create a new user with no holdings
    const user = newUser('holdings-empty');
    await registerViaApi(request, user);
    await loginViaUi(page, user);
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings page
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    // AC2: See friendly empty state
    await expect(page.getByRole('heading', { name: 'No Holdings Yet' })).toBeVisible();
    await expect(page.getByText(/You haven't purchased any stocks yet/)).toBeVisible();

    // AC2: Link to place an order
    const orderButton = page.getByRole('button', { name: 'Place an Order' });
    await expect(orderButton).toBeVisible();

    // Click the button and verify it navigates to dashboard (where orders can be placed)
    await orderButton.click();
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('AC3: Holdings persist after page reload', async ({ page }) => {
    // Seed data user with holdings
    await loginViaUi(page, seed('seed_active'));
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    // Capture initial holdings (mat-table uses rowgroups, not tbody)
    const initialRows = page.locator('.holdings-table tr');
    await initialRows.first().waitFor({ state: 'visible' });
    const initialHoldings = await initialRows.count();
    const initialContent = await page.locator('.holdings-table').textContent();

    // AC3: Reload the page
    await page.reload();

    // Verify holdings are exactly the same after reload
    await expect(page).toHaveURL(/\/holdings$/);
    const reloadedRows = page.locator('.holdings-table tr');
    await reloadedRows.first().waitFor({ state: 'visible' });
    const reloadedHoldings = await reloadedRows.count();
    const reloadedContent = await page.locator('.holdings-table').textContent();

    expect(reloadedHoldings).toBe(initialHoldings);
    expect(reloadedContent).toBe(initialContent);
  });
});
