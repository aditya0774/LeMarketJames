import { expect, test } from '@playwright/test';
import { loginViaUi, newUser, registerViaApi } from './support/users';

test.describe('Authentication', () => {
  test('a new user can register, log in, and log out', async ({ page }) => {
    const user = newUser('reg');

    await page.goto('/register');
    // Fields are addressed by id: several labels repeat ("Password", the "Show password" toggles).
    await page.locator('#firstName').fill('Jane');
    await page.locator('#lastName').fill('Tester');
    await page.locator('#dateOfBirth').fill('1990-01-01');
    // The form formats digits as they are typed, e.g. (555) 123-4567 and 123-45-6789.
    await page.locator('#phoneNumber').fill('5551234567');
    await page.locator('#email').fill(user.email);
    await page.locator('#streetAddress').fill('123 Main St');
    await page.locator('#city').fill('Springfield');
    await page.locator('#state').selectOption('Illinois');
    await page.locator('#zipCode').fill('62701');
    await page.locator('#ssn').fill('123456789');
    await page.locator('#initialDeposit').fill('10000');
    await page.locator('#username').fill(user.username);
    await page.locator('#password').fill(user.password);
    await page.locator('#confirmPassword').fill(user.password);
    await page.getByRole('button', { name: 'Create account' }).click();

    // A successful registration sends the user to the login page.
    await expect(page).toHaveURL(/\/login$/);

    await loginViaUi(page, user);
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByText(`Welcome back, ${user.username}`)).toBeVisible();

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/$/);
  });

  test('a wrong password is rejected with an error', async ({ page, request }) => {
    const user = newUser('badpw');
    await registerViaApi(request, user);

    await loginViaUi(page, user, 'Wrong123!');

    await expect(page.getByText('Login failed')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('the dashboard requires a login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });
});
