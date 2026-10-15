import { expect, test } from '@playwright/test';
import { newUser, registerViaApi } from './support/users';

const LOCKOUT_WAIT_MS = Number(process.env.E2E_LOCKOUT_WAIT_MS ?? 3_000);

async function submitLogin(page: import('@playwright/test').Page, email: string, password: string) {
  const response = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname === '/api/auth/login';
  });
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  return response;
}

async function openLogin(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/login');
  await expect(page).toHaveURL(/\/login$/);
}

test.describe('Account lockout (LMKT-11)', () => {
  test.describe.configure({ mode: 'serial' });

  test('locks, refuses, unlocks, and resets a real customer login', async ({ page, request }) => {
    const user = newUser('lock');
    await registerViaApi(request, user);
    await openLogin(page);

    // AC3: a successful login after fewer than three failures resets the counter.
    for (let attempt = 0; attempt < 2; attempt++) {
      const response = await submitLogin(page, user.email, 'Wrong123!');
      expect(response.status()).toBe(400);
      expect((await response.json()).message).toBe('Invalid email or password');
      await expect(page.locator('[role="alert"]')).toContainText('Invalid email or password');
    }
    const resetLogin = await submitLogin(page, user.email, user.password);
    expect(resetLogin.status()).toBe(200);
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/$/);
    await openLogin(page);

    // AC1: the third consecutive failure locks the account and reports its duration.
    for (let attempt = 0; attempt < 3; attempt++) {
      const response = await submitLogin(page, user.email, 'Wrong123!');
      expect(response.status()).toBe(400);
    }
    await expect(page.locator('[role="alert"]')).toContainText(/Account locked for \d+ minute/);

    // AC2: the correct password is still refused during the lockout.
    const blockedLogin = await submitLogin(page, user.email, user.password);
    expect(blockedLogin.status()).toBe(400);
    await expect(page.locator('[role="alert"]')).toContainText(/Try again in \d+ minute/);

    // AC4: after the disposable stack's short lockout duration, the same password succeeds.
    await page.waitForTimeout(LOCKOUT_WAIT_MS);
    const unlockedLogin = await submitLogin(page, user.email, user.password);
    expect(unlockedLogin.status()).toBe(200);
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
