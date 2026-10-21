import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AnalystDashboard } from './analyst-dashboard';
import { flagFor } from './insight-flags/insight-flag.fixtures';

describe('AnalystDashboard', () => {
  async function create() {
    await TestBed.configureTestingModule({
      imports: [AnalystDashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AnalystDashboard);
    await fixture.whenStable();
    return fixture;
  }

  it('opens with the "What changed" strip, which lists flagged insights first as the tiles load', async () => {
    const fixture = await create();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.firstElementChild!.tagName.toLowerCase()).toBe('staff-what-changed');

    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/reports/insights/most-active-stocks').flush({
      startDate: '2026-10-12', endDate: '2026-10-14', previousStartDate: '2026-10-05', previousEndDate: '2026-10-07',
      stocks: [
        { rank: 1, symbol: 'AAPL', tradedValue: 1000, tradeCount: 2, shareVolume: 5, previousTradedValue: 990, flag: flagFor(1.01) },
        { rank: 2, symbol: 'TSLA', tradedValue: 900, tradeCount: 1, shareVolume: 3, previousTradedValue: 0, flag: flagFor(null) },
      ],
      generatedAt: '2026-10-14T14:00:00Z',
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const rows = Array.from(el.querySelectorAll('staff-what-changed li'));
    expect(rows.map((r) => r.getAttribute('data-key'))).toEqual(['stock-TSLA', 'stock-AAPL']);
    expect(rows[0].querySelector('staff-insight-flag .flag')).not.toBeNull();
  });

  it('shows the volume insight tile, which asks for today, this week and last week', async () => {
    const fixture = await create();

    expect((fixture.nativeElement as HTMLElement).querySelector('staff-volume-insight-tile')).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    // It loads its cards once it knows the reports time zone.
    http.expectOne('/api/v1/reports/ping').flush({ success: true, service: 'staff-ops-service', timeZone: 'America/New_York' });
    expect(http.match((r) => r.url === '/api/v1/reports/insights/volume/compare').length).toBe(3);
  });

  it('shows the active-client trend tile, which asks for the trend once', async () => {
    const fixture = await create();

    expect((fixture.nativeElement as HTMLElement).querySelector('staff-active-client-trend-tile')).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    expect(http.match((r) => r.url === '/api/v1/reports/insights/active-clients').length).toBe(1);
  });

  it('shows the most-active-stocks tile, which asks for the current week', async () => {
    const fixture = await create();

    expect((fixture.nativeElement as HTMLElement).querySelector('staff-most-active-stocks-tile')).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    expect(http.match((r) => r.url === '/api/v1/reports/insights/most-active-stocks').length).toBe(1);
  });
});
