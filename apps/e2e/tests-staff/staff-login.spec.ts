import { expect, Page, test } from '@playwright/test';
import { ANALYST, CLIENT, signIn, TRADING_OPS } from './support/staff';

/**
 * LMKT-139: staff sign in on the staff app and each role gets its own pages
 * (contracts/C7-roles.md). Everything goes through the real staff app, staff gateway and
 * auth-service, without mocks. The tests only sign in and read, so they share the seed logins.
 */
const REPORTS = ['Trade activity by period', 'Activity by stock', 'Activity by client segment', 'Overnight reports'];

async function expectAccessDenied(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/access-denied$/);
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
}

test.describe('Staff login', () => {
  test('the app opens on the staff login, and so does every page for a signed-out visitor', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Staff Login' })).toBeVisible();

    for (const address of ['/trade-search', '/analyst', '/analyst/reports/activity-by-stock']) {
      await page.goto(address);
      await expect(page, address).toHaveURL(/\/login$/);
    }
  });

  test('Trading Ops lands on the Trading Ops dashboard', async ({ page }) => {
    await signIn(page, TRADING_OPS);

    await expect(page).toHaveURL(/\/trade-search$/);
    await expect(page.getByRole('heading', { name: 'Trade search', exact: true })).toBeVisible();
  });

  test('an Analyst lands on the Analyst dashboard and sees the report list', async ({ page }) => {
    await signIn(page, ANALYST);

    await expect(page).toHaveURL(/\/analyst$/);
    await expect(page.getByRole('heading', { name: 'Analyst dashboard' })).toBeVisible();
    await expect(page.getByRole('main').getByRole('listitem')).toHaveText(REPORTS);

    await page.getByRole('link', { name: 'Activity by stock' }).click();
    await expect(page).toHaveURL(/\/analyst\/reports\/activity-by-stock$/);
  });

  test('Trading Ops is refused on the Analyst pages', async ({ page }) => {
    await signIn(page, TRADING_OPS);
    await expect(page).toHaveURL(/\/trade-search$/);
    await expect(page.getByRole('link', { name: 'Analyst', exact: true })).toHaveCount(0);

    // Typed into the address bar: a full page load, so the session is read back from the cookie.
    for (const address of ['/analyst', '/analyst/reports/activity-by-stock']) {
      await page.goto(address);
      await expectAccessDenied(page);
    }
    await expect(page.getByRole('heading', { name: 'Analyst dashboard' })).toHaveCount(0);
  });

  test('an Analyst is refused on the Trading Ops pages', async ({ page }) => {
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/analyst$/);
    await expect(page.getByRole('link', { name: 'Trading Ops', exact: true })).toHaveCount(0);

    for (const address of ['/trade-search']) {
      await page.goto(address);
      await expectAccessDenied(page);
    }
    // /trading-ops is not a route; wildcard navigation returns to the role's home.
    await page.goto('/trading-ops');
    await expect(page).toHaveURL(/\/analyst$/);
    await expect(page.getByRole('heading', { name: 'Trading Ops Dashboard' })).toHaveCount(0);
  });

  test('a client is refused on the staff login and is left signed out', async ({ page, context }) => {
    await signIn(page, CLIENT);

    await expect(page.getByRole('alert')).toContainText('This login is for staff only');
    await expect(page).toHaveURL(/\/login$/);
    // The gateway gave the client a staff session cookie on sign-in; it must be gone again,
    // so the gateway no longer knows this browser.
    await expect.poll(async () => (await context.cookies()).map(cookie => cookie.name)).not.toContain('staff_jwt');
    expect((await page.request.get('/api/auth/me')).status()).toBe(401);

    for (const address of ['/trading-ops', '/analyst']) {
      await page.goto(address);
      await expect(page, address).toHaveURL(/\/login$/);
    }
  });

  test('signing out returns to the staff login and ends the session', async ({ page }) => {
    await signIn(page, TRADING_OPS);
    await expect(page).toHaveURL(/\/trade-search$/);

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto('/trade-search');
    await expect(page).toHaveURL(/\/login$/);
  });
});
