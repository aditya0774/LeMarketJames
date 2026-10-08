import { Page } from '@playwright/test';
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
  await page.goto('/login');
  await page.locator('#username').fill(user.email);
  await page.locator('#password').fill(user.password);
  // For valid staff logins, wait for navigation. For clients/invalid logins, the page may stay on /login.
  // Use waitForNavigation with a short timeout, and ignore timeouts (some logins don't navigate).
  await Promise.all([
    page.waitForNavigation({ timeout: 5000 }).catch(() => {}),
    page.getByRole('button', { name: 'Log in' }).click(),
  ]);
}
