import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests run against an already running stack; they never start the app themselves.
 * - Jenkins: the Docker Compose stack, where the nginx frontend on :4200 proxies /api to the gateway.
 * - Windows: scripts/windows/start-all.ps1, where `ng serve` on :4200 proxies /api to the gateway.
 * Either way the browser and the API calls go through the same origin a real user would use.
 */
const isCi = !!process.env.CI;

export default defineConfig({
  testDir: './tests',
  // Every test registers its own user, so tests never share state and can run in parallel.
  fullyParallel: true,
  forbidOnly: isCi,
  // CI must expose flakiness instead of hiding it with automatic retries.
  retries: 0,
  workers: isCi ? 2 : undefined,
  reporter: isCi
    ? [['list'], ['junit', { outputFile: 'results/junit.xml' }], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    // Keep evidence only when something fails, so passing runs stay fast and small.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
