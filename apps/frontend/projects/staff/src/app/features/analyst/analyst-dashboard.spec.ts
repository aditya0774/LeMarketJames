import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AnalystDashboard } from './analyst-dashboard';

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

  it('shows the volume insight tile, which asks for today, this week and last week', async () => {
    const fixture = await create();

    expect((fixture.nativeElement as HTMLElement).querySelector('staff-volume-insight-tile')).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    // It loads its cards once it knows the reports time zone.
    http.expectOne('/api/v1/reports/ping').flush({ success: true, service: 'reporting-service', timeZone: 'America/New_York' });
    expect(http.match((r) => r.url === '/api/v1/reports/insights/volume/compare').length).toBe(3);
  });

  it('shows the active-client trend tile, which asks for the trend once', async () => {
    const fixture = await create();

    expect((fixture.nativeElement as HTMLElement).querySelector('staff-active-client-trend-tile')).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    expect(http.match((r) => r.url === '/api/v1/reports/insights/active-clients').length).toBe(1);
  });
});
