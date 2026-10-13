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

  it('lists a link to every report', async () => {
    const fixture = await create();

    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.report-card .report-title'));
    expect(links.map((a) => a.textContent)).toEqual([
      'Trade Activity by Period',
      'Activity by Stock',
      'Activity by Client Segment',
      'Overnight Reports',
    ]);
  });

  it('shows the volume insight tile, which asks for the day and week insights', async () => {
    const fixture = await create();

    expect((fixture.nativeElement as HTMLElement).querySelector('staff-volume-insight-tile')).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    expect(http.match((r) => r.url === '/api/v1/reports/insights/volume').length).toBe(2);
  });
});
