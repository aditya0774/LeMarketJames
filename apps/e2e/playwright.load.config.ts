import { defineConfig } from '@playwright/test';
import tradingConfig from './playwright.config';

/**
 * LMKT-44: the report load test (load/report-load.spec.ts). Everything not set here is the trading
 * suite's configuration (playwright.config.ts), so it runs against the same already running stack.
 *
 * It is a separate configuration because it runs for minutes and must not share the machine with
 * other tests: anything else running at the same time would distort the timings it compares.
 * It drives the APIs only, so it needs no browser.
 */
const isCi = !!process.env.CI;
const htmlReport: ['html', object] = ['html', { open: 'never', outputFolder: 'playwright-report-load' }];

export default defineConfig({
  ...tradingConfig,
  testDir: './load',
  outputDir: 'test-results-load',
  fullyParallel: false,
  workers: 1,
  // Two measured phases plus fixtures; the phase lengths are settings in the spec.
  timeout: 15 * 60_000,
  reporter: isCi
    ? [['list'], ['junit', { outputFile: 'results/load-junit.xml' }], htmlReport]
    : [['list'], htmlReport],
  projects: [{ name: 'load' }],
});
