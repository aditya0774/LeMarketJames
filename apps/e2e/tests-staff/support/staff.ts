import { expect, Page } from '@playwright/test';
import { TestUser } from '../../tests/support/users';

/**
 * The seed logins (contracts/C3-seed-data.md, loaded by 011), which every environment starts
 * with. Staff logins cannot be registered, so the staff suite shares these. Only sign in with
 * the right password: a few wrong ones lock the login out for every suite that uses it.
 */
const seedLogin = (name: string): TestUser =>
  ({ username: name, email: `${name}@seed.lemarket.com`, password: 'Pass123!' });

export const TRADING_OPS = seedLogin('ops');
export const ANALYST = seedLogin('analyst');
/** An active client, for checking that the staff login turns clients away. */
export const CLIENT = seedLogin('seed_active');

/** Signs in through the staff login page, the way staff do. Where it lands is the test's to check. */
export async function signIn(page: Page, user: TestUser): Promise<void> {
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  // Checked on its own so a login page that never draws fails after the expect timeout, naming
  // the missing form, instead of waiting out the whole test inside fill().
  await expect(page.locator('#username')).toBeVisible();
  await page.locator('#username').fill(user.email);
  await page.locator('#password').fill(user.password);
  const loginResponse = page.waitForResponse(
    response => response.url().includes('/api/auth/login'),
  );
  await page.getByRole('button', { name: 'Log in' }).click();
  const response = await loginResponse;
  if (!response.ok()) {
    throw new Error(`Staff login failed: ${response.status()} ${await response.text()}`);
  }
}

/**
 * Waits until the Analyst dashboard has stopped asking for its tiles. Its report queries share the
 * reporting service's single database connection and keep running after the browser leaves the
 * page, so a test that times a report right after signing in would be timing that queue as well.
 */
export async function waitForAnalystDashboard(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/analyst$/);
  await page.waitForLoadState('networkidle');
}

/** The heading of the page a refused role is sent to. */
export const ACCESS_DENIED_HEADING = "You don't have access to this page";

/** Logs out the way staff do: "Log out" sits in the account menu behind the avatar button. */
export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('button', { name: 'Log out' }).click();
}

/** The analyst workspace tabs, as named on the tab bar. */
export type AnalystTab = 'Volume' | 'By period' | 'By stock' | 'By segment' | 'Overnight';

/** Opens an analyst report the way an analyst does, through the tab bar. */
export async function openAnalystTab(page: Page, tab: AnalystTab): Promise<void> {
  await page.getByRole('navigation', { name: 'Analyst reports' }).getByRole('link', { name: tab, exact: true }).click();
}
