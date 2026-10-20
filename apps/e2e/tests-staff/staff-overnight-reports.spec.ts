import { expect, Page, test } from '@playwright/test';
import { Client } from 'pg';
import { connectDatabase } from '../tests/support/database';
import { ANALYST, openAnalystTab, signIn, TRADING_OPS } from './support/staff';

/**
 * LMKT-45 end to end: the overnight reports list through the real staff app and
 * staff-ops-service. The scheduler only runs at night, so the runs are written straight into
 * report_runs, one per status the list must show; the page and the API must show exactly them.
 */
const REPORT = '/analyst/reports/overnight-reports';
const API = '/api/v1/reports/overnight';
const CONTENT = 'staff-overnight-reports';
/** Every run this spec writes is named with this, so cleanup touches nothing else. */
const PREFIX = 'E2E overnight';

// Two nights ago (UTC), inside the list's 30-night window and clear of tonight's real runs.
const runDate = new Date(Date.now() - 2 * 86_400_000).toISOString().slice(0, 10);
const night = new Date(Date.parse(`${runDate}T00:00:00Z`) + 86_400_000).toISOString().slice(0, 10);
const at = (time: string) => `${night}T${time}Z`;

interface SeededRun {
  name: string;
  status: 'READY' | 'LATE' | 'FAILED';
  readyAt: string | null;
  failureReason: string | null;
}

const SCHEDULED_FOR = at('06:00:00');
const READY_BY = at('11:00:00');
const RUNS: SeededRun[] = [
  { name: `${PREFIX} ready`, status: 'READY', readyAt: at('06:04:00'), failureReason: null },
  { name: `${PREFIX} late, finished`, status: 'LATE', readyAt: at('11:12:00'), failureReason: null },
  { name: `${PREFIX} late, still going`, status: 'LATE', readyAt: null, failureReason: null },
  { name: `${PREFIX} failed`, status: 'FAILED', readyAt: null, failureReason: 'Source data missing (E2E)' },
];

// The tests share the runs written once below, and one of them changes a run: in one worker, in
// order, or another worker's cleanup would delete the runs while a test still reads them.
test.describe.configure({ mode: 'serial' });

async function removeSeededRuns(db: Client) {
  await db.query('DELETE FROM report_runs WHERE report_name LIKE $1', [`${PREFIX}%`]);
}

test.beforeAll(async () => {
  const db = await connectDatabase();
  try {
    await removeSeededRuns(db);
    for (const run of RUNS) {
      await db.query(
        `INSERT INTO report_runs (report_name, run_date, status, scheduled_for, ready_by, started_at, ready_at, failure_reason)
         VALUES ($1, $2, $3, $4, $5, $4, $6, $7)`,
        [run.name, runDate, run.status, SCHEDULED_FOR, READY_BY, run.readyAt, run.failureReason]);
    }
  } finally { await db.end(); }
});

test.afterAll(async () => {
  const db = await connectDatabase();
  try { await removeSeededRuns(db); } finally { await db.end(); }
});

/** A time as the page shows it: in the reports time zone the API names, not the browser's. */
function shown(isoUtc: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-US', {
    timeZone, year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short',
  }).format(new Date(isoUtc)).replace(/\s+/g, ' ');
}

/** Signs in as the analyst and opens the list from the dashboard, returning the API's body. */
async function openList(page: Page) {
  await signIn(page, ANALYST);
  await expect(page).toHaveURL(/\/analyst$/);
  const response = page.waitForResponse(r => new URL(r.url()).pathname === API);
  await openAnalystTab(page, 'Overnight');
  const answer = await response;
  expect(answer.status(), await answer.text()).toBe(200);
  // Through the staff app's own origin, never staff-ops-service directly.
  expect(new URL(answer.url()).origin).toBe(new URL(page.url()).origin);
  await expect(page).toHaveURL(new RegExp(`${REPORT}$`));
  return answer.json();
}

const row = (page: Page, name: string) => page.locator(`${CONTENT} tbody tr`, { hasText: name });

test('LMKT-45 AC2/AC3: the list shows each run with its status, ready time and failure reason', async ({ page }) => {
  const body = await openList(page);

  expect(Object.keys(body).sort()).toEqual(['data', 'success', 'timeZone']);
  const timeZone: string = body.timeZone;
  for (const seeded of RUNS) {
    const run = body.data.find((r: { name: string }) => r.name === seeded.name);
    expect(run, `${seeded.name} is in the API's list`).toBeTruthy();
    // An allowlist: nothing beyond the C6 fields, so nothing that could identify a client.
    expect(Object.keys(run).sort()).toEqual(
      ['failureReason', 'id', 'name', 'readyAt', 'readyBy', 'runDate', 'scheduledFor', 'status']);
    expect(run).toMatchObject({ runDate, status: seeded.status, failureReason: seeded.failureReason });
    // Instants are compared as instants, so the API may write seconds or fractions either way.
    expect(run.readyAt === null ? null : Date.parse(run.readyAt))
      .toBe(seeded.readyAt === null ? null : Date.parse(seeded.readyAt));
    expect(Date.parse(run.scheduledFor)).toBe(Date.parse(SCHEDULED_FOR));
    expect(Date.parse(run.readyBy)).toBe(Date.parse(READY_BY));
  }

  await expect(page.locator(`${CONTENT} .zone`)).toHaveText(`Times in ${timeZone}`);

  const ready = row(page, `${PREFIX} ready`);
  await expect(ready.locator('.status-badge')).toHaveText('Ready');
  await expect(ready.locator('.ready-time')).toHaveText(shown(RUNS[0].readyAt!, timeZone), { useInnerText: true });
  await expect(ready.locator('.run-date')).toHaveText(`For ${runDate}`);

  const lateFinished = row(page, `${PREFIX} late, finished`);
  await expect(lateFinished.locator('.status-badge')).toHaveText('Late');
  await expect(lateFinished.locator('.ready-time')).toHaveText(shown(RUNS[1].readyAt!, timeZone), { useInnerText: true });
  await expect(lateFinished.locator('.late-note')).toHaveText(
    `Missed the ready-by time, ${shown(READY_BY, timeZone)}`, { useInnerText: true });

  const lateGoing = row(page, `${PREFIX} late, still going`);
  await expect(lateGoing.locator('.status-badge')).toHaveText('Late');
  await expect(lateGoing.locator('.ready-time')).toHaveText('Not ready yet');

  const failed = row(page, `${PREFIX} failed`);
  await expect(failed.locator('.status-badge')).toHaveText('Failed');
  await expect(failed.locator('.failure-reason')).toHaveText('Source data missing (E2E)');
  await expect(failed.locator('.ready-time')).toHaveText('—');
});

test('LMKT-45: Refresh asks the server again and shows a run that changed meanwhile', async ({ page }) => {
  await openList(page);
  await expect(row(page, `${PREFIX} late, still going`).locator('.ready-time')).toHaveText('Not ready yet');

  // The late run finishes while the page is open.
  const db = await connectDatabase();
  try {
    await db.query('UPDATE report_runs SET ready_at = $2 WHERE report_name = $1',
      [`${PREFIX} late, still going`, at('11:30:00')]);
  } finally { await db.end(); }

  const refreshed = page.waitForResponse(r => new URL(r.url()).pathname === API);
  await page.getByRole('button', { name: 'Refresh' }).click();
  const { timeZone } = await (await refreshed).json();
  await expect(row(page, `${PREFIX} late, still going`).locator('.ready-time'))
    .toHaveText(shown(at('11:30:00'), timeZone), { useInnerText: true });
});

test('LMKT-45: signed-out visitors cannot open the list or read it', async ({ page }) => {
  expect((await page.request.get(API)).status()).toBe(401);
  await page.goto(REPORT);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator(CONTENT)).toHaveCount(0);
});

test('LMKT-45: Trading Ops cannot open the list or bypass it with the API', async ({ page }) => {
  await signIn(page, TRADING_OPS);
  await expect(page).toHaveURL(/\/trade-search$/);
  expect((await page.request.get(API)).status()).toBe(403);

  // A full reload: the app restores the session, then the role guard refuses the page. toHaveURL
  // waits for that on its own; a lost session would end on /login instead and fail here.
  await page.goto(REPORT, { waitUntil: 'domcontentloaded' });
  await expect(page).toHaveURL(/\/access-denied$/, { timeout: 30_000 });
  await expect(page.locator(CONTENT)).toHaveCount(0);
});
