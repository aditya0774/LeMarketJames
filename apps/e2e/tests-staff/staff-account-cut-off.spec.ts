import { expect, test } from '@playwright/test';
import { connectDatabase, createStaffUser, deleteStaffUser, setStaffActive } from '../tests/support/database';
import { newUser, TestUser } from '../tests/support/users';
import { ANALYST, signIn } from './support/staff';

/**
 * LMKT-58 (S06-INT): a staff login deactivated while signed in to the staff app loses the session
 * on its next request (AC2), the same as a closed client in the trading app (account-cut-off.spec.ts).
 *
 * AC3, the security event with the account, reason and UTC time, is covered by
 * JwtAuthenticationFilterTest and AuthServiceLoggingTest (ACCESS_REVOKED with username, reason
 * and UTC time): Playwright runs in its own container in Jenkins and cannot read the service logs.
 *
 * Staff cannot register, so the test adds its own throwaway Analyst with the seed Analyst's
 * password and deletes it afterwards. The shared seed logins are never deactivated.
 */
const INACTIVE_MESSAGE = 'This account is no longer active. Please contact the firm.';

test.describe('Staff account cut off mid-session', () => {
  test('a staff login deactivated while signed in is signed out on the next request', async ({ page }) => {
    const staff: TestUser = { ...newUser('staff'), password: ANALYST.password };
    const db = await connectDatabase();
    try {
      await createStaffUser(db, staff, 'ANALYST', ANALYST);

      await signIn(page, staff);
      await expect(page).toHaveURL(/\/analyst$/);
      await expect(page.getByRole('heading', { name: 'Trading volume', exact: true })).toBeVisible();

      await setStaffActive(db, staff.username, false);

      // The staff gateway forwards the browser's staff_jwt; the account behind it is checked on every request.
      const response = await page.request.get('/api/auth/me');
      expect(response.status()).toBe(401);
      expect((await response.json()).code).toBe('ACCOUNT_INACTIVE');

      // A full reload would land on a plain login page: the session is restored before the first
      // navigation, so the interceptor leaves the redirect to the auth guard. The tile only reloads
      // itself every 30 s, so Refresh makes the in-app request that sends the user back with the reason.
      await page.locator('staff-volume-insight-tile').getByRole('button', { name: 'Refresh' }).click();
      await expect(page).toHaveURL(/\/login\?reason=inactive$/);
      await expect(page.getByRole('alert')).toContainText('Account not active');
      await expect(page.getByRole('alert')).toContainText(INACTIVE_MESSAGE);
      await expect(page.getByRole('heading', { name: 'Trading volume', exact: true })).toHaveCount(0);

      // The cookie no longer opens the app either.
      await page.goto('/analyst');
      await expect(page).toHaveURL(/\/login$/);
    } finally {
      try {
        await deleteStaffUser(db, staff.username);
      } finally {
        await db.end();
      }
    }
  });
});
