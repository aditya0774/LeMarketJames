import { expect, Page, test } from '@playwright/test';
import { signIn, TRADING_OPS } from './support/staff';

/**
 * BR-03 on the staff app: the same inactivity timeout as the trading app (shared-ui
 * SessionTimeout). It also proves the staff Nginx (or dev proxy) routes POST /api/auth/refresh
 * and renames its cookie to staff_jwt, or "Stay signed in" would fail.
 *
 * The browser's clock is faked and jumped forward, so the test passes whatever the stack's
 * timeout is (tests/session-timeout.spec.ts explains). It signs the seed Trading Ops login out,
 * like the sign-out test in staff-login.spec.ts.
 */
async function refreshDone(page: Page): Promise<void> {
  const response = await page.waitForResponse(r => r.url().endsWith('/api/auth/refresh'));
  expect(response.status()).toBe(200);
  // The services' jwt reaches the browser as the staff cookie.
  // (headers() leaves Set-Cookie out; allHeaders() has it.)
  expect((await response.allHeaders())['set-cookie']).toMatch(/^staff_jwt=/);
}

test('an idle staff member is warned, can stay signed in, and is signed out when nobody answers', async ({ page, request }) => {
  await page.clock.install();

  const opened = refreshDone(page);
  await signIn(page, TRADING_OPS);
  await expect(page).toHaveURL(/\/trade-search$/);
  await opened;
  const session = await (await page.request.get('/api/auth/me')).json();
  const timeoutMs = session.inactivityTimeoutSeconds * 1000;

  await page.clock.fastForward(timeoutMs - 20_000);
  const warning = page.getByRole('alertdialog', { name: 'Are you still there?' });
  await expect(warning).toBeVisible();

  const stayed = refreshDone(page);
  await warning.getByRole('button', { name: 'Stay signed in' }).click();
  await stayed;
  await expect(warning).toBeHidden();

  const cookie = (await page.context().cookies()).find(c => c.name === 'staff_jwt')!;
  await page.clock.fastForward(timeoutMs + 1_000);

  await expect(page).toHaveURL(/\/login\?reason=timeout$/);
  await expect(page.getByRole('alert')).toContainText('You were signed out after a period of inactivity.');

  const reused = await request.get('/api/auth/me', { headers: { Cookie: `staff_jwt=${cookie.value}` } });
  expect(reused.status()).toBe(401);
});
