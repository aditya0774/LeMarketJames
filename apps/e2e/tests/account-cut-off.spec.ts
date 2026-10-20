import { expect, test } from '@playwright/test';
import { connectDatabase, setClientStatus } from './support/database';
import { loginViaUi, newUser, registerViaApi } from './support/users';

/**
 * LMKT-58 (S06-INT): a client whose account is closed or expired while they are signed in loses
 * the session on its next request (AC2). Refusal at sign-in (AC1) is in seed-data.spec.ts.
 *
 * AC3, the security event with the account, reason and UTC time, is covered by
 * JwtAuthenticationFilterTest and AuthServiceLoggingTest (ACCESS_REVOKED with username, reason
 * and UTC time): Playwright runs in its own container in Jenkins and cannot read the service logs.
 *
 * Each test closes its own freshly registered client, never a shared seed account, so the tests
 * stay independent and safe to run in parallel.
 */
const INACTIVE_MESSAGE = 'This account is no longer active. Please contact the firm.';
// registerViaApi's opening deposit, shown as buying power while the client is signed in.
const DEPOSIT = '$10,000.00';

test.describe('Account cut off mid-session', () => {
  for (const status of ['CLOSED', 'EXPIRED']) {
    test(`a client whose account becomes ${status} is signed out on the next request`, async ({ page, request }) => {
      const user = newUser('cut');
      await registerViaApi(request, user);
      await loginViaUi(page, user);
      await expect(page).toHaveURL(/\/dashboard$/);
      await expect(page.getByText(`Welcome back, ${user.username}`)).toBeVisible();
      await expect(page.locator('app-stat-strip')).toContainText(DEPOSIT);

      const db = await connectDatabase();
      try {
        await setClientStatus(db, user.username, status);
      } finally {
        await db.end();
      }

      // The browser's cookie is still valid, but the account behind it is checked on every request.
      const response = await page.request.get('/api/v1/portfolio');
      expect(response.status()).toBe(401);
      expect((await response.json()).code).toBe('ACCOUNT_INACTIVE');

      // A full reload would land on a plain login page: the session is restored before the first
      // navigation, so the interceptor leaves the redirect to the auth guard. The dashboard's own
      // quote refresh (every 2 s) is the in-app request that sends the client back with the reason.
      await expect(page).toHaveURL(/\/login\?reason=inactive$/);
      await expect(page.getByRole('alert')).toContainText('Account not active');
      await expect(page.getByRole('alert')).toContainText(INACTIVE_MESSAGE);

      // Nothing from the signed-in session is left on screen.
      await expect(page.getByText(`Welcome back, ${user.username}`)).toHaveCount(0);
      await expect(page.getByText('Buying power')).toHaveCount(0);
      await expect(page.getByText(DEPOSIT)).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'What you own' })).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'Quick trade' })).toHaveCount(0);

      // The cookie no longer opens the app either.
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/\/login$/);
    });
  }
});
