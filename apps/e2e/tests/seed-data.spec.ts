import { expect, test } from '@playwright/test';
import { TestUser, loginViaUi } from './support/users';

/**
 * The seed data set (contracts/C3-seed-data.md) that every environment starts with. These tests
 * only read seed data, so they can share the seed logins safely.
 */
const SEED_PASSWORD = 'Pass123!';
const seed = (name: string): TestUser => ({
  username: name,
  email: `${name}@seed.lemarket.com`,
  password: SEED_PASSWORD,
});

test.describe('Seed data', () => {
  test('the main seed client sees their order history', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText('Welcome back, seed_active')).toBeVisible();

    // Orders across every status: e.g. the rejected CAVS order is in the list.
    const orders = page.locator('section.panel', { has: page.getByRole('heading', { name: 'Orders' }) });
    await expect(orders.getByText(/Showing 1–\d+ of \d+ orders/)).toBeVisible();
    // Holdings come from the seeded fills.
    const portfolio = page.locator('section.panel', { has: page.getByRole('heading', { name: 'Portfolio' }) });
    await expect(portfolio.getByRole('row').filter({ hasText: 'AAPL' })).toBeVisible();
  });

  test('the main seed client filters multi-year history and clears it', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);
    const orders = page.locator('app-orders-panel');
    const info = orders.locator('.pg-info');
    await expect(info).toContainText(/of \d+ orders/);
    const fullCount = Number((await info.textContent())?.match(/of (\d+) orders/)?.[1]);

    const year = String(new Date().getFullYear());
    await orders.getByLabel('Period', { exact: true }).selectOption('year');
    const filteredResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname.includes('/orders/account/') && url.searchParams.get('date') === year;
    });
    await orders.locator('.date-filters input').fill(year);
    expect((await filteredResponse).status()).toBe(200);
    await expect(info).toContainText(/of \d+ orders/);
    const filteredCount = Number((await info.textContent())?.match(/of (\d+) orders/)?.[1]);
    expect(filteredCount).toBeGreaterThan(0);
    expect(filteredCount).toBeLessThan(fullCount);

    const clearedResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname.includes('/orders/account/') && !url.search;
    });
    await orders.getByRole('button', { name: 'Clear filter' }).click();
    expect((await clearedResponse).status()).toBe(200);
    await expect(info).toContainText(`of ${fullCount} orders`);
  });

  test('the suspended stock is listed but cannot be traded', async ({ page }) => {
    await loginViaUi(page, seed('seed_active'));
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.getByLabel('Filter stocks').fill('CAVS');
    const row = page.getByRole('row', { name: 'Trade CAVS', exact: true });
    await expect(row).toContainText('Not tradable');

    await row.click();
    const dialog = page.getByRole('dialog', { name: 'Trade CAVS' });
    await expect(dialog.getByText('CAVS is not currently tradable.')).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Place buy order' })).toBeDisabled();
  });

  test('a closed client cannot log in', async ({ page }) => {
    await loginViaUi(page, seed('seed_closed'));

    await expect(page.getByText('Login failed')).toBeVisible();
    await expect(page.getByText('not active')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });
});
