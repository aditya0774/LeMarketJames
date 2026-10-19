import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests run against an already running stack; they never start the app themselves.
 * - Jenkins: the Docker Compose stack, where the nginx frontend on :4200 proxies /api to the services.
 * - Windows: scripts/windows/start-all.ps1, where `ng serve` on :4200 proxies /api to the services.
 * Either way the browser and the API calls go through the same origin a real user would use.
 */
const isCi = !!process.env.CI;
const FEED_FAILURE_TESTS = /feed-failure\.spec\.ts$/;
// The files that pause the execution worker for the whole stack.
const EXECUTION_RETRY_TESTS = /(execution-retry|settlement)\.spec\.ts$/;
const LOCKOUT_TESTS = /lockout\.spec\.ts$/;

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
  // The Jenkins VM also runs the complete application stack, Kafka, PostgreSQL and Chromium.
  // Leave enough room for requests to finish when those processes contend for CPU and memory;
  // local runs keep their shorter feedback loop.
  timeout: isCi ? 240_000 : 60_000,
  expect: { timeout: isCi ? 30_000 : 10_000 },
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
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: [FEED_FAILURE_TESTS, EXECUTION_RETRY_TESTS, LOCKOUT_TESTS],
    },
    {
      name: 'execution-recovery',
      workers: 1,
      use: { ...devices['Desktop Chrome'] },
      testMatch: EXECUTION_RETRY_TESTS,
      dependencies: ['chromium'],
    },
    {
      name: 'lockout-recovery',
      workers: 1,
      use: { ...devices['Desktop Chrome'] },
      testMatch: LOCKOUT_TESTS,
      dependencies: ['chromium', 'execution-recovery'],
    },
    // These tests make the quote feed stale or unavailable for the whole stack (contract C4),
    // which would refuse every other test's orders. Depending on the main project makes them
    // start only once it has finished; Playwright skips them if it failed. They also wait for the
    // project above: its tests place orders that a broken feed would refuse, and these tests'
    // orders would never execute while that project has the worker paused.
    {
      name: 'feed-failure',
      // Different files still share one global feed; serialize all outage scenarios.
      workers: 1,
      use: { ...devices['Desktop Chrome'] },
      testMatch: FEED_FAILURE_TESTS,
      dependencies: ['chromium', 'execution-recovery', 'lockout-recovery'],
    },
  ],
});
