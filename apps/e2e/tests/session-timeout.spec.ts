import { expect, Page, test } from '@playwright/test';
import { loginViaUi, newUser, registerViaApi } from './support/users';

/**
 * BR-03: a client who leaves the app alone is warned 30 seconds before the inactivity timeout
 * (lmj.session.inactivity-timeout, contracts/C5-config.md), can stay signed in, and is signed out
 * with the reason when nobody answers; the old session cookie is refused afterwards.
 *
 * The browser's clock is faked (page.clock) and jumped forward, so the test takes seconds and
 * passes whatever the stack's timeout is: one minute by default, or the PT30M that the e2e runs
 * use (SESSION_INACTIVITY_TIMEOUT). Each test signs in a client of its own.
 */
const WARNING = 'Are you still there?';

async function refreshDone(page: Page): Promise<void> {
  const response = await page.waitForResponse(r => r.url().endsWith('/api/auth/refresh'));
  expect(response.status()).toBe(200);
}

test('an idle client is warned, can stay signed in, and is signed out when nobody answers', async ({ page, request }) => {
  const user = newUser('idle');
  await registerViaApi(request, user);
  await page.clock.install();

  // Opening the signed-in pages counts as activity: the session is extended once.
  const opened = refreshDone(page);
  await loginViaUi(page, user);
  await expect(page).toHaveURL(/\/dashboard$/);
  await opened;
  const session = await (await page.request.get('/api/auth/me')).json();
  const timeoutMs = session.inactivityTimeoutSeconds * 1000;

  // Nobody touches the app until 20 seconds are left.
  await page.clock.fastForward(timeoutMs - 20_000);
  const warning = page.getByRole('alertdialog', { name: WARNING });
  await expect(warning).toBeVisible();
  await expect(warning).toContainText(/signed out in \d+ seconds/);

  const stayed = refreshDone(page);
  await warning.getByRole('button', { name: 'Stay signed in' }).click();
  await stayed;
  await expect(warning).toBeHidden();
  await expect(page).toHaveURL(/\/dashboard$/);

  // Idle again, past the whole timeout this time.
  const cookie = (await page.context().cookies()).find(c => c.name === 'jwt')!;
  await page.clock.fastForward(timeoutMs + 1_000);

  await expect(page).toHaveURL(/\/login\?reason=timeout$/);
  await expect(page.getByRole('alert')).toContainText('Signed out');
  await expect(page.getByRole('alert')).toContainText('You were signed out after a period of inactivity.');
  await expect(page.getByText(`Welcome back, ${user.username}`)).toHaveCount(0);

  // The session is over on the server too, not only on screen.
  const reused = await request.get('/api/auth/me', { headers: { Cookie: `jwt=${cookie.value}` } });
  expect(reused.status()).toBe(401);
});
