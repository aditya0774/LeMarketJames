import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActiveClientTrendResponse } from '../../../shared/models/active-client-trend.model';
import { ActiveClientTrendTile } from './active-client-trend-tile';

const URL = '/api/v1/reports/insights/active-clients';

/** 30 days in the C6 shape: counts 0..29 unless overridden, the last day being today. */
function trend(counts?: number[]): ActiveClientTrendResponse {
  const values = counts ?? Array.from({ length: 30 }, (_, i) => i);
  return {
    startDate: '2026-09-15',
    endDate: '2026-10-14',
    days: values.map((activeClients, i) => ({
      date: new Date(Date.UTC(2026, 8, 15 + i)).toISOString().slice(0, 10),
      activeClients,
    })),
    generatedAt: '2026-10-14T14:00:00Z',
  };
}

describe('ActiveClientTrendTile', () => {
  let fixture: ComponentFixture<ActiveClientTrendTile>;
  let httpMock: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActiveClientTrendTile],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActiveClientTrendTile);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  async function flush(body: ActiveClientTrendResponse = trend()) {
    httpMock.expectOne(URL).flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('asks for the trend once and shows loading', () => {
    expect(el().textContent).toContain('Loading');
    httpMock.expectOne(URL).flush(trend());
  });

  it('draws one bar for each of the 30 days and the window dates', async () => {
    await flush();

    expect(el().querySelectorAll('rect.bar').length).toBe(30);
    expect(el().querySelector('.range')!.textContent).toContain('2026-09-15 – 2026-10-14');
    expect(el().querySelector('svg')!.getAttribute('aria-label')).toContain('peak 29');
  });

  it("shows today's count and the peak", async () => {
    await flush(trend([3, 12, 7, ...Array(26).fill(0), 5]));

    expect(el().querySelector('.latest')!.textContent).toContain('5');
    expect(el().querySelector('.peak')!.textContent).toContain('12');
  });

  it('shows the last-refreshed time', async () => {
    await flush();

    expect(el().querySelector('.refreshed')!.textContent).toContain('Last refreshed');
  });

  it('says so when no client traded in the window', async () => {
    await flush(trend(Array(30).fill(0)));

    expect(el().querySelector('.empty')!.textContent).toContain('No trades');
    expect(el().querySelectorAll('rect.bar').length).toBe(30);
  });

  it('shows an error when the request fails', async () => {
    httpMock.expectOne(URL).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')).not.toBeNull();
    expect(el().querySelector('svg')).toBeNull();
  });

  it('Refresh asks again', async () => {
    await flush();

    (el().querySelector('button.refresh') as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock.expectOne(URL).flush(trend());
  });

  it('a background reload keeps the figures on screen, even when it fails', async () => {
    await flush();

    fixture.componentInstance.refresh(true);
    fixture.detectChanges();
    expect(el().textContent).not.toContain('Loading');

    httpMock.expectOne(URL).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(el().querySelectorAll('rect.bar').length).toBe(30);
    expect(el().querySelector('[role="alert"]')).toBeNull();
  });
});
