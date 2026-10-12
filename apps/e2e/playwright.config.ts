import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests run against an already running stack; they never start the app themselves.
 * - Jenkins: the Docker Compose stack, where the nginx frontend on :4200 proxies /api to the gateway.
 * - Windows: scripts/windows/start-all.ps1, where `ng serve` on :4200 proxies /api to the gateway.
 * Either way the browser and the API calls go through the same origin a real user would use.
 */
const isCi = !!process.env.CI;
const FEED_FAILURE_TESTS = /feed-failure\.spec\.ts$/;

export default defineConfig({
  testDir: './tests',
  // Every test registers its own user, so tests never share state and can run in parallel.
  fullyParallel: true,
  forbidOnly: isCi,
  // CI must expose flakiness instead of hiding it with automatic retries.
  retries: 0,
  // The Jenkins agent also hosts the whole Docker stack (a dozen JVMs plus Kafka and Postgres).
  // Two parallel Chromium instances starved it of CPU: pages froze mid-login and the whole
  // browser session closed. One worker is slower but stable; E2E_WORKERS raises it on a bigger agent.
  workers: isCi ? Number(process.env.E2E_WORKERS ?? 1) : undefined,
  // Room for a login, a seeded page load and a trace teardown on a busy agent.
  timeout: isCi ? 60_000 : 30_000,
  expect: { timeout: isCi ? 10_000 : 5_000 },
  reporter: isCi
    ? [['list'], ['junit', { outputFile: 'results/junit.xml' }], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    // Keep evidence only when something fails, so passing runs stay fast and small.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Recording video costs CPU the CI agent lacks; the trace and screenshot already show the failure.
    video: isCi ? 'off' : 'retain-on-failure',
    launchOptions: {
      // Containers have a small /dev/shm; without this Chromium can crash or hang under load.
      args: ['--disable-dev-shm-usage', '--disable-gpu'],
    },
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] }, testIgnore: FEED_FAILURE_TESTS },
    // These tests make the quote feed stale or unavailable for the whole stack (contract C4),
    // which would refuse every other test's orders. Depending on the main project makes them
    // start only once it has finished; Playwright skips them if it failed.
    {
      name: 'feed-failure',
      // Different files still share one global feed; serialize all outage scenarios.
      workers: 1,
      use: { ...devices['Desktop Chrome'] },
      testMatch: FEED_FAILURE_TESTS,
      dependencies: ['chromium'],
    },
  ],
});
