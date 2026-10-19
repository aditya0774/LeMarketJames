import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OvernightReports } from './overnight-reports';
import { OvernightReport, OvernightReportsResponse } from '@staff/shared/models/overnight-report.model';

const URL = '/api/v1/reports/overnight';

/** A run in the C6 shape; each test overrides only what it is about. */
const run = (overrides: Partial<OvernightReport>): OvernightReport => ({
  id: 'r1',
  name: 'Activity by stock',
  runDate: '2026-10-13',
  status: 'SCHEDULED',
  scheduledFor: '2026-10-14T06:00:00Z',
  readyBy: '2026-10-14T11:00:00Z',
  readyAt: null,
  failureReason: null,
  ...overrides,
});

const response = (data: OvernightReport[]): OvernightReportsResponse => ({
  success: true,
  timeZone: 'America/New_York',
  data,
});

describe('OvernightReports', () => {
  let fixture: ComponentFixture<OvernightReports>;
  let http: HttpTestingController;
  const page = () => fixture.nativeElement as HTMLElement;
  // Collapse the narrow no-break space some ICU versions put before AM/PM.
  const text = () => (page().textContent ?? '').replace(/\s+/g, ' ');

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OvernightReports],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OvernightReports);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Answers the pending list request and renders the result. */
  const answer = (body: OvernightReportsResponse) => {
    http.expectOne(URL).flush(body);
    fixture.detectChanges();
  };

  const fail = (status: number) => {
    http.expectOne(URL).flush('error', { status, statusText: 'Error' });
    fixture.detectChanges();
  };

  it('asks for the overnight reports on open and shows a spinner meanwhile', () => {
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('GET');
    expect(page().querySelector('output.loading')?.textContent).toContain('Loading overnight reports');

    req.flush(response([]));
    fixture.detectChanges();
    expect(page().querySelector('output.loading')).toBeNull();
  });

  it('says which time zone the times are in', () => {
    answer(response([run({})]));
    expect(text()).toContain('Times in America/New_York');
  });

  it('shows a ready run with the time it was ready, in the reports time zone (AC2)', () => {
    answer(response([run({ status: 'READY', readyAt: '2026-10-14T06:04:00Z' })]));

    const row = page().querySelector('tr[data-status="READY"]')!;
    expect(row.querySelector('.status-badge')?.textContent).toContain('Ready');
    expect(row.querySelector('.ready-time')?.textContent?.replace(/\s+/g, ' ')).toContain('Oct 14, 2026, 2:04 AM EDT');
  });

  it('shows a scheduled or running run with the time it is expected by (AC2)', () => {
    answer(response([run({ id: 'a', status: 'SCHEDULED' }), run({ id: 'b', status: 'RUNNING' })]));

    for (const status of ['SCHEDULED', 'RUNNING']) {
      const ready = page().querySelector(`tr[data-status="${status}"] .ready-time`)?.textContent?.replace(/\s+/g, ' ');
      expect(ready).toContain('Expected by Oct 14, 2026, 7:00 AM EDT');
    }
    expect(page().querySelector('tr[data-status="RUNNING"] .status-badge')?.textContent).toContain('Running');
  });

  it('shows a failed run as failed, with its reason (AC3)', () => {
    answer(response([run({ status: 'FAILED', failureReason: 'Timed out reading reporting_trades' })]));

    const row = page().querySelector('tr[data-status="FAILED"]')!;
    expect(row.querySelector('.status-badge')?.textContent).toContain('Failed');
    expect(row.querySelector('.failure-reason')?.textContent).toContain('Timed out reading reporting_trades');
  });

  it('still marks a failed run without a recorded reason (AC3)', () => {
    answer(response([run({ status: 'FAILED', failureReason: null })]));
    expect(page().querySelector('tr[data-status="FAILED"] .failure-reason')?.textContent).toContain('No reason recorded');
  });

  it('shows a run that finished after the ready-by time as late, with when it finished (AC2)', () => {
    answer(response([run({ status: 'LATE', readyAt: '2026-10-14T11:12:00Z' })]));

    const row = page().querySelector('tr[data-status="LATE"]')!;
    const squash = (s?: string | null) => s?.replace(/\s+/g, ' ');
    expect(row.querySelector('.status-badge')?.textContent).toContain('Late');
    expect(squash(row.querySelector('.ready-time')?.textContent)).toContain('Oct 14, 2026, 7:12 AM EDT');
    expect(squash(row.querySelector('.late-note')?.textContent)).toContain('Missed the ready-by time, Oct 14, 2026, 7:00 AM EDT');
  });

  it('shows a late run that is still going as not ready yet (AC2)', () => {
    answer(response([run({ status: 'LATE', readyAt: null })]));

    const row = page().querySelector('tr[data-status="LATE"]')!;
    expect(row.querySelector('.ready-time')?.textContent).toContain('Not ready yet');
    expect(row.querySelector('.failure-reason')).toBeNull();
  });

  it('lists every run in the order the server sends', () => {
    answer(response([
      run({ id: 'a', name: 'Activity by stock', runDate: '2026-10-13', status: 'FAILED', failureReason: 'x' }),
      run({ id: 'b', name: 'Activity by stock', runDate: '2026-10-12', status: 'READY', readyAt: '2026-10-13T06:10:00Z' }),
    ]));

    const dates = Array.from(page().querySelectorAll('.run-date')).map((d) => d.textContent?.trim());
    expect(dates).toEqual(['For 2026-10-13', 'For 2026-10-12']);
  });

  it('says so when no overnight report has run yet', () => {
    answer(response([]));
    expect(text()).toContain('No overnight reports have run yet.');
    expect(page().querySelector('table')).toBeNull();
  });

  it('explains a 403', () => {
    fail(403);
    expect(page().querySelector('[role="alert"]')?.textContent).toContain('do not have permission');
  });

  it('explains a 401', () => {
    fail(401);
    expect(page().querySelector('[role="alert"]')?.textContent).toContain('session has expired');
  });

  it('shows a generic message for any other failure, and Try Again asks again', () => {
    fail(500);
    expect(page().querySelector('[role="alert"]')?.textContent).toContain('Unable to load overnight reports');

    (page().querySelector('button.retry') as HTMLButtonElement).click();
    answer(response([run({ status: 'READY', readyAt: '2026-10-14T06:04:00Z' })]));

    expect(page().querySelector('[role="alert"]')).toBeNull();
    expect(page().querySelector('tr[data-status="READY"]')).not.toBeNull();
  });

  it('treats success: false as a failure', () => {
    answer({ ...response([]), success: false });
    expect(page().querySelector('[role="alert"]')?.textContent).toContain('Unable to load overnight reports');
  });

  it('Refresh asks again, so a run that finished meanwhile shows up', () => {
    answer(response([run({ status: 'RUNNING' })]));

    (page().querySelector('button.refresh') as HTMLButtonElement).click();
    answer(response([run({ status: 'READY', readyAt: '2026-10-14T06:04:00Z' })]));

    expect(page().querySelector('tr[data-status="RUNNING"]')).toBeNull();
    expect(page().querySelector('tr[data-status="READY"]')).not.toBeNull();
  });
});
