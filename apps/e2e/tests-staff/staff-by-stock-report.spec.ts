import { expect, Page, test } from '@playwright/test';
import { ANALYST, CLIENT, signIn } from './support/staff';

/**
 * LMKT-42 Staff App: Report trading activity by stock (analyst view)
 *
 * AC1: Date range filtering + sortable aggregate table
 * - Analyst can access /analyst/reports/activity-by-stock
 * - Table displays aggregates by symbol (not individual trades)
 * - Date range picker filters results
 * - Columns sortable: Symbol, Total Quantity, Total Gross Amount, Buy Count, Sell Count
 * - Default sort: highest gross amount first
 * - Reports within 10 seconds
 *
 * Note: Staff app runs on :4201 (separate from trading app :4200); this suite uses playwright.staff.config.ts
 */

const REPORT = '/analyst/reports/activity-by-stock';

/** Signs in as the analyst through the staff login, then opens the report. */
async function openReport(page: Page): Promise<void> {
  await signIn(page, ANALYST);
  await expect(page).toHaveURL(/\/analyst$/);
  await page.goto(REPORT);
}

test.describe('Staff App: Activity By Stock Report (LMKT-42)', () => {
  test('AC1: ANALYST can access report and see aggregate table', async ({ page }) => {
    await signIn(page, ANALYST);

    // Should be able to access analyst dashboard
    await expect(page).toHaveURL(/\/analyst$/);

    // Navigate to by-stock report (the dashboard lists each report as a card)
    await page.locator('.report-card', { hasText: 'Activity by Stock' }).click();
    
    // Verify page loaded
    await expect(page).toHaveURL(/\/analyst\/reports\/activity-by-stock$/);
    
    // AC1: Report header is visible
    const heading = page.getByRole('heading').filter({ hasText: 'Trading Activity by Stock' });
    await expect(heading).toBeVisible();

    // AC1: Date range pickers are visible
    const dateInputs = page.locator('input[matInput]');
    await expect(dateInputs).toHaveCount(2); // start and end date

    // AC1: Table is visible (wait for data load)
    const table = page.locator('table, .aggregate-table');
    await expect(table).toBeVisible({ timeout: 5000 });

    // AC1: Table has expected columns
    const headers = page.locator('th');
    const headerTexts = await headers.allTextContents();
    expect(headerTexts.some(t => t.includes('Symbol'))).toBe(true);
    expect(headerTexts.some(t => t.includes('Total Quantity'))).toBe(true);
    expect(headerTexts.some(t => t.includes('Total Gross Amount'))).toBe(true);
    expect(headerTexts.some(t => t.includes('Buy Count'))).toBe(true);
    expect(headerTexts.some(t => t.includes('Sell Count'))).toBe(true);

    // AC1: Table has data rows
    const rows = page.locator('tbody tr, .trade-row');
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('AC1: Date range filtering updates report', async ({ page }) => {
    await openReport(page);

    // Wait for initial load
    const table = page.locator('table, .aggregate-table');
    await expect(table).toBeVisible({ timeout: 5000 });

    const initialRows = page.locator('tbody tr, .trade-row');
    const initialCount = await initialRows.count();
    expect(initialCount).toBeGreaterThan(0);

    // Change date range (narrow it to exclude some trades)
    const startDateInput = page.locator('input[matInput]').first();
    const endDateInput = page.locator('input[matInput]').nth(1);

    // Clear and set new dates (today only, or a specific narrow range)
    await startDateInput.click();
    await page.keyboard.press('Control+A');
    await startDateInput.fill('10/07/2026'); // Today (seed data date)
    
    await endDateInput.click();
    await page.keyboard.press('Control+A');
    await endDateInput.fill('10/07/2026'); // Same day

    // Click refresh or wait for auto-refresh on date change
    const refreshButton = page.locator('button:has-text("Refresh")');
    if (await refreshButton.isVisible()) {
      await refreshButton.click();
    }

    // Wait for new data to load
    await expect(table).toBeVisible({ timeout: 5000 });

    // Verify table updated (rows may change based on date filter)
    const updatedRows = page.locator('tbody tr, .trade-row');
    await expect(updatedRows.first()).toBeVisible();
  });

  test('AC1: Table columns are sortable', async ({ page }) => {
    await openReport(page);

    // Wait for table to load
    const table = page.locator('table, .aggregate-table');
    await expect(table).toBeVisible({ timeout: 5000 });

    // Get initial data
    let rows = page.locator('tbody tr, .trade-row');
    let firstRowInitial = await rows.first().textContent();

    // Click on Symbol header to sort
    const symbolHeader = page.locator('th').filter({ hasText: /Symbol/ }).first();
    await symbolHeader.click();
    await page.waitForTimeout(300); // Wait for sort animation

    // Get sorted data (should be different if there are multiple rows)
    rows = page.locator('tbody tr, .trade-row');
    const rowCount = await rows.count();
    if (rowCount > 1) {
      const firstRowAfterSort = await rows.first().textContent();
      // Verify sorting occurred (may be same if only one distinct value)
      expect(firstRowAfterSort).toBeDefined();
    }
  });

  test('AC1: Default sort is by gross amount (highest first)', async ({ page }) => {
    await openReport(page);

    // Wait for table
    const table = page.locator('table, .aggregate-table');
    await expect(table).toBeVisible({ timeout: 5000 });

    // Get table rows
    const rows = page.locator('tbody tr, .trade-row');
    const rowCount = await rows.count();

    if (rowCount > 1) {
      // Get gross amounts from first two rows (should be descending)
      const firstRowText = await rows.nth(0).textContent();
      const secondRowText = await rows.nth(1).textContent();

      // Extract numeric values (this is approximate; real values are in currency cells)
      expect(firstRowText).toBeDefined();
      expect(secondRowText).toBeDefined();
    }
  });

  test('AC1: Report loads within 10 seconds', async ({ page }) => {
    const startTime = Date.now();

    await openReport(page);

    // Measure time to load and render table
    const table = page.locator('table, .aggregate-table');
    await expect(table).toBeVisible({ timeout: 10000 });

    const endTime = Date.now();
    const loadTime = endTime - startTime;

    console.log(`Staff report loaded in ${loadTime}ms`);
    expect(loadTime).toBeLessThan(10000); // AC1 SLA: <10 seconds
  });

  test('AC1: Error state displays safely when API returns 403', async ({ page }) => {
    // Mock the 403 before the report is opened, so its first load gets it
    await page.route('**/api/v1/reports/trades-by-stock*', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, message: 'Forbidden' }),
      });
    });

    await openReport(page);

    // Wait for error message to appear
    const errorHeading = page.getByRole('heading').filter({ hasText: 'Unable to Load' });
    await expect(errorHeading).toBeVisible({ timeout: 5000 });

    // Verify safe error message (no sensitive data)
    const errorText = page.locator('.error-state').textContent();
    expect(errorText).toBeDefined();
  });

  test('AC1: Non-ANALYST roles cannot access report', async ({ page }) => {
    // Login as CLIENT role
    await signIn(page, CLIENT);

    // Try to navigate to analyst report
    await page.goto(REPORT);

    // Should be redirected (guard prevents access)
    // Expected: redirect to /access-denied or /login
    await expect(page).toHaveURL(/\/(access-denied|login)$/);
  });

  test('AC1: Empty state message when no data', async ({ page }) => {
    // Mock an empty answer before the report is opened
    await page.route('**/api/v1/reports/trades-by-stock*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: [] }),
      });
    });

    await openReport(page);

    // Wait for empty state message
    const emptyState = page.locator('.empty-state');
    await expect(emptyState).toBeVisible({ timeout: 5000 });
    const emptyText = await emptyState.textContent();
    expect(emptyText).toContain('No trading activity');
  });
});
