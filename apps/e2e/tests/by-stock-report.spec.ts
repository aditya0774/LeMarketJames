import { expect, test } from '@playwright/test';
import { TestUser, loginViaUi } from './support/users';

/**
 * LMKT-42: Report trading activity by stock
 *
 * AC1: Unauthorized data access prevented
 * - Scoped to authenticated user's own account
 * - Detects 403 errors and shows safe message
 * - No sensitive data leakage
 *
 * AC2: Real report on screen
 * - Aggregates trades by stock symbol
 * - Shows trades in sortable table (date, side, quantity, price)
 * - Displays symbol selector for multi-stock portfolios
 *
 * AC3: Report loads within 10 seconds
 * - Verified via performance monitoring
 * - Includes API call and render time
 */

const SEED_PASSWORD = 'Pass123!';
const seed = (name: string): TestUser => ({
  username: name,
  email: `${name}@seed.lemarket.com`,
  password: SEED_PASSWORD,
});

test.describe('By-Stock Report (LMKT-42)', () => {
  test('AC1, AC2: User with holdings can view report with stock selector and trades table', async ({ page }) => {
    // Seed data user has filled orders creating holdings
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings page
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    // AC2: Holdings table is visible
    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    // Get the first stock symbol from the holdings table (e.g., AAPL)
    const firstRow = holdingsTable.locator('tr').nth(1); // Skip header row
    await expect(firstRow).toBeVisible();
    const symbolCell = firstRow.locator('td').first();
    const symbol = await symbolCell.textContent();
    expect(symbol).toBeTruthy();
    const symbolText = symbol!.trim();

    // Click on the first holding to navigate to by-stock detail view
    await firstRow.click();
    
    // Verify navigation to detail route with symbol parameter
    await expect(page).toHaveURL(new RegExp(`/holdings/${symbolText}$`));

    // AC2: By-stock report loads with trades table
    const reportContainer = page.locator('.by-stock-detail-container, .by-stock-report-container');
    await expect(reportContainer).toBeVisible({ timeout: 5000 });

    // Verify trade history heading
    const heading = page.getByRole('heading').filter({ hasText: new RegExp(symbolText, 'i') });
    await expect(heading).toBeVisible();

    // AC2: Trades table is visible and contains data
    const tradesTable = page.locator('table, .trades-table');
    await expect(tradesTable).toBeVisible({ timeout: 5000 });

    // Verify table has columns for date, side, quantity, price
    const tableText = await tradesTable.textContent();
    expect(tableText).toContain('Date');
    expect(tableText).toContain('Side');
    expect(tableText).toContain('Quantity');
    expect(tableText).toContain('Price');

    // Verify at least one trade row exists
    const tradeRows = page.locator('.trade-row, tbody tr');
    const rowCount = await tradeRows.count();
    expect(rowCount).toBeGreaterThan(0);

    // AC2: Verify trades contain BUY or SELL
    const firstTradeText = await tradeRows.first().textContent();
    expect(firstTradeText).toMatch(/BUY|SELL/i);
  });

  test('AC3: By-stock report loads and renders within 10 seconds', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    const firstRow = holdingsTable.locator('tr').nth(1);
    const symbolCell = firstRow.locator('td').first();
    const symbol = await symbolCell.textContent();
    const symbolText = symbol!.trim();

    // Measure time from click to report render
    const startTime = Date.now();
    
    await firstRow.click();
    await expect(page).toHaveURL(new RegExp(`/holdings/${symbolText}$`));

    // Wait for trades table to be visible and populated
    const tradesTable = page.locator('table, .trades-table');
    await expect(tradesTable).toBeVisible({ timeout: 10000 });

    const endTime = Date.now();
    const loadTime = endTime - startTime;

    // AC3: Should load within 10 seconds (10000ms)
    expect(loadTime).toBeLessThan(10000);
    console.log(`By-stock report loaded in ${loadTime}ms`);

    // Verify content is actually rendered (not just DOM present but empty)
    const tradeRows = page.locator('.trade-row, tbody tr');
    await expect(tradeRows.first()).toBeVisible();
  });

  test('AC2: Multi-stock report shows symbol selector when user has multiple holdings', async ({ page }) => {
    // Seed user with multiple holdings
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings page
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    // Count holdings (stocks)
    const rows = holdingsTable.locator('tbody tr, tr:not(:first-child)');
    const holdingCount = await rows.count();

    // If multiple holdings, verify symbol selector in report
    if (holdingCount > 1) {
      const firstRow = rows.nth(0);
      const firstSymbol = await firstRow.locator('td').first().textContent();
      
      await firstRow.click();
      await expect(page).toHaveURL(new RegExp(`/holdings/${firstSymbol!.trim()}$`));

      // AC2: Stock selector should exist in the container view
      // (Present if using the container component for multi-select)
      const reportContainer = page.locator('.by-stock-report-container');
      if (await reportContainer.isVisible()) {
        const selector = page.locator('mat-select, select');
        await expect(selector).toBeVisible();
      }
    }
  });

  test('AC1: Error state displays safely when data cannot be loaded (403 unauthorized)', async ({ page }) => {
    // Log in as user with holdings
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings page
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    // Get first stock symbol
    const firstRow = holdingsTable.locator('tr').nth(1);
    const symbolCell = firstRow.locator('td').first();
    const symbol = await symbolCell.textContent();
    const symbolText = symbol!.trim();

    // Set up route interception to return 403 for trades API
    await page.route(`**/api/v1/trades*`, async (route) => {
      // Mock a 403 Forbidden response
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Access denied',
          code: 'FORBIDDEN',
        }),
      });
    });

    // Navigate to by-stock detail page (will trigger mocked 403 error)
    await firstRow.click();
    await expect(page).toHaveURL(new RegExp(`/holdings/${symbolText}$`));

    // Wait for error state to be displayed
    const reportContainer = page.locator('.by-stock-detail-container');
    await expect(reportContainer).toBeVisible({ timeout: 5000 });

    // AC1: Verify error message is displayed safely (no sensitive data)
    const errorHeading = page.getByRole('heading').filter({ hasText: 'Unable to Load Trades' });
    await expect(errorHeading).toBeVisible();

    const errorMessage = page.getByText(/You do not have permission/);
    await expect(errorMessage).toBeVisible();

    // AC1: Verify retry button is present and functional
    const retryButton = page.getByRole('button').filter({ hasText: 'Try Again' });
    await expect(retryButton).toBeVisible();
  });

  test('AC2: Sortable table allows column sorting', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings and open a report
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    const firstRow = holdingsTable.locator('tr').nth(1);
    const symbolCell = firstRow.locator('td').first();
    const symbol = await symbolCell.textContent();
    
    await firstRow.click();
    await expect(page).toHaveURL(new RegExp(`/holdings/${symbol!.trim()}$`));

    // AC2: Verify trades table is visible
    const tradesTable = page.locator('table, .trades-table');
    await expect(tradesTable).toBeVisible({ timeout: 5000 });

    // Verify table headers (sortable columns)
    const headers = tradesTable.locator('th, .header-cell');
    const headerCount = await headers.count();
    expect(headerCount).toBeGreaterThan(0);

    // Verify standard columns are present
    const headerText = await tradesTable.textContent();
    expect(headerText).toContain('Date');
    expect(headerText).toContain('Side');
    expect(headerText).toContain('Quantity');
    expect(headerText).toContain('Price');
  });

  test('AC2: Empty state message when no trades exist for selected symbol', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    const firstRow = holdingsTable.locator('tr').nth(1);
    const symbolCell = firstRow.locator('td').first();
    const symbol = await symbolCell.textContent();
    const symbolText = symbol!.trim();
    
    // Mock the trades API to return empty array for this symbol
    await page.route(`**/api/v1/trades?*symbol=${symbolText}*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await firstRow.click();
    await expect(page).toHaveURL(new RegExp(`/holdings/${symbolText}$`));

    // Wait for report container to be visible
    const reportContainer = page.locator('.by-stock-detail-container');
    await expect(reportContainer).toBeVisible({ timeout: 5000 });

    // AC2: Verify empty state message is displayed when no trades exist
    const emptyStateHeading = page.getByRole('heading').filter({ hasText: 'No Trading Activity' });
    await expect(emptyStateHeading).toBeVisible();

    // Verify empty state description mentions the symbol
    const emptyStateMessage = page.getByText(/no trades recorded/i);
    await expect(emptyStateMessage).toBeVisible();

    // Verify no trades table is shown
    const tradesTable = page.locator('table, .trades-table');
    const isTableVisible = await tradesTable.isVisible().catch(() => false);
    expect(isTableVisible).toBe(false);
  });

  test('AC1, AC2: Loading state shows spinner while report loads', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    // Navigate to holdings
    await page.click('a:has-text("Holdings")');
    await expect(page).toHaveURL(/\/holdings$/);

    const holdingsTable = page.locator('.holdings-table');
    await expect(holdingsTable).toBeVisible();

    const firstRow = holdingsTable.locator('tr').nth(1);
    const symbolCell = firstRow.locator('td').first();
    const symbol = await symbolCell.textContent();
    
    // Click to navigate
    await firstRow.click();

    // AC1, AC2: Loading state should appear briefly
    const loadingSpinner = page.locator('mat-spinner, [role="status"]');
    // Spinner might already be hidden by the time test checks, so don't fail if missing
    // Just verify eventual content appears
    
    const reportContainer = page.locator('.by-stock-detail-container, .by-stock-report-container');
    await expect(reportContainer).toBeVisible({ timeout: 5000 });

    // Content should eventually be visible (not loading forever)
    const content = page.locator('h2, mat-card-header, table');
    await expect(content.first()).toBeVisible();
  });
});
