import { APIRequestContext, Page, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';

export interface TestUser {
  username: string;
  email: string;
  password: string;
}

/**
 * A brand-new user per test, so tests stay independent and can run in parallel against a
 * shared database. The username fits the form's 3-20 character rule, and the email ends in
 * .com as the registration form requires.
 */
export function newUser(prefix = 'e2e'): TestUser {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
  const username = `${prefix}${suffix}`.slice(0, 20);
  return { username, email: `${username}@example.com`, password: 'Pass123!' };
}

/**
 * Registers through the API instead of the form, for tests whose subject is something else;
 * the registration form itself is covered in auth.spec.ts. Uses the same payload as the
 * Jenkins API smoke tests.
 */
export async function registerViaApi(request: APIRequestContext, user: TestUser): Promise<void> {
  const response = await request.post('/api/auth/register', {
    data: {
      username: user.username,
      email: user.email,
      password: user.password,
      fullName: 'E2E Tester',
      streetAddress: '123 Main St',
      city: 'Springfield',
      state: 'Illinois',
      zipCode: '62701',
      country: 'US',
      ssn: '123-45-6789',
      initialDeposit: 10000,
      investmentExperience: 'beginner',
      employmentStatus: 'employed',
      dateOfBirth: '1990-01-01',
      phoneNumber: '(555) 123-4567',
    },
  });
  expect(response.status(), await response.text()).toBe(201);
}

/** Logs in through the login page, the way a user does, and waits for the dashboard. */
export async function loginViaUi(page: Page, user: TestUser, password = user.password): Promise<void> {
  await page.goto('/login');
  // Registration stores the email as the login name, so the "Email address" field takes it.
  await page.locator('#username').fill(user.email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}
