import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { TradeActivityByPeriod } from './trade-activity-by-period';
import { TradeReportResponse } from '../../../../shared/models/trade-report.model';

/** A response shaped like the C6 example for GET /api/v1/reports/trades (mocked API). */
const dayReport: TradeReportResponse = {
  data: [
    { period: '2026-10-02', tradeCount: 2, buyCount: 1, sellCount: 1, totalValue: 500 },
    { period: '2026-10-01', tradeCount: 5, buyCount: 3, sellCount: 2, totalValue: 1250.5 },
  ],
  generatedAt: '2026-10-07T20:15:30.123Z',
};

describe('TradeActivityByPeriod', () => {
  let fixture: ComponentFixture<TradeActivityByPeriod>;
  let component: TradeActivityByPeriod;
  let http: HttpTestingController;

  const isReport = (req: { url: string }) => req.url === '/api/v1/reports/trades';

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TradeActivityByPeriod],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TradeActivityByPeriod);
    component = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;

  it('loads the MONTH report with a date range as soon as it opens', () => {
    const req = http.expectOne(isReport);
    expect(req.request.params.get('periodType')).toBe('MONTH');
    expect(req.request.params.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(req.request.params.get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    req.flush({ data: [], generatedAt: '' });
  });

  it('shows one row per period with its counts and value, plus a total row', async () => {
    http.expectOne(isReport).flush({ data: [], generatedAt: '' });
    component.selectPeriodType('DAY');
    http.expectOne(isReport).flush(dayReport);
    await fixture.whenStable();

    const rows = el().querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('October 2, 2026');
    expect(rows[0].textContent).toContain('$500.00');
    expect(rows[1].textContent).toContain('$1,250.50');

    const footer = el().querySelector('tr[mat-footer-row]')!.textContent!;
    expect(footer).toContain('7'); // 2 + 5 trades
    expect(footer).toContain('$1,750.50');
  });

  it('reloads with the chosen period type', () => {
    http.expectOne(isReport).flush({ data: [], generatedAt: '' });

    component.selectPeriodType('WEEK');

    const req = http.expectOne(isReport);
    expect(req.request.params.get('periodType')).toBe('WEEK');
    req.flush({ data: [], generatedAt: '' });
  });

  it('reloads with the chosen date range when Apply is used', () => {
    http.expectOne(isReport).flush({ data: [], generatedAt: '' });

    component.range.setValue({ from: new Date(2026, 0, 5), to: new Date(2026, 0, 20) });
    component.applyRange();

    const req = http.expectOne(isReport);
    expect(req.request.params.get('from')).toBe('2026-01-05');
    expect(req.request.params.get('to')).toBe('2026-01-20');
    req.flush({ data: [], generatedAt: '' });
  });

  it('does not call the API when the start date is after the end date', () => {
    http.expectOne(isReport).flush({ data: [], generatedAt: '' });

    component.range.setValue({ from: new Date(2026, 0, 20), to: new Date(2026, 0, 5) });
    component.applyRange();

    expect(component.error()).toContain('start date');
    http.expectNone(isReport);
  });

  it('asks for both dates when one is missing', () => {
    http.expectOne(isReport).flush({ data: [], generatedAt: '' });

    component.range.setValue({ from: null, to: new Date(2026, 0, 5) });
    component.applyRange();

    expect(component.error()).toContain('both');
    http.expectNone(isReport);
  });

  it('says there are no trades when the report is empty', async () => {
    http.expectOne(isReport).flush({ data: [], generatedAt: '' });
    await fixture.whenStable();

    expect(el().textContent).toContain('No trades in this date range.');
    expect(el().querySelector('table')).toBeNull();
  });

  it.each([
    [401, 'session has expired'],
    [403, 'cannot read reports'],
    [400, 'not accepted'],
    [500, 'could not be loaded'],
  ])('shows a message for a %i response', async (status, text) => {
    http.expectOne(isReport).flush({}, { status, statusText: 'x' });
    await fixture.whenStable();

    expect(el().querySelector('[role="alert"]')!.textContent).toContain(text);
    expect(el().textContent).not.toContain('No trades in this date range.');
  });
});
