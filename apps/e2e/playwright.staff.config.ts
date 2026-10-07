import { defineConfig, devices } from '@playwright/test';
import tradingConfig from './playwright.config';

/**
 * End-to-end tests of the staff app (apps/frontend/projects/staff). Everything not set here is
 * the trading suite's configuration (playwright.config.ts), so both suites run the same way
 * against the same already running stack:
 * - Jenkins: the Docker Compose stack, where the staff Nginx on :4201 proxies /api to the staff gateway.
 * - Windows: scripts/windows/start-all.ps1, where `ng serve staff` on :4201 does the same.
 *
 * It is a separate configuration rather than another project in playwright.config.ts because
 * the staff app is another origin: these tests take their relative addresses from it, and they
 * keep their own reports so a run of one suite never overwrites the other's.
 */
const isCi = !!process.env.CI;
const htmlReport: ['html', object] = ['html', { open: 'never', outputFolder: 'playwright-report-staff' }];

export default defineConfig({
  ...tradingConfig,
  testDir: './tests-staff',
  outputDir: 'test-results-staff',
  reporter: isCi
    ? [['list'], ['junit', { outputFile: 'results/staff-junit.xml' }], htmlReport]
    : [['list'], htmlReport],
  use: {
    ...tradingConfig.use,
    // The same variable staff-gateway.spec.ts uses for the staff app's address.
    baseURL: process.env.E2E_STAFF_BASE_URL ?? 'http://localhost:4201',
  },
  projects: [{ name: 'staff', use: { ...devices['Desktop Chrome'] } }],
});
