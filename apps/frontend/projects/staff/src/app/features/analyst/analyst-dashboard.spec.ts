import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AnalystDashboard } from './analyst-dashboard';

describe('AnalystDashboard', () => {
  it('lists a link to every report', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalystDashboard],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(AnalystDashboard);
    await fixture.whenStable();

    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('ul a'));
    expect(links.map((a) => a.textContent)).toEqual([
      'Trade activity by period',
      'Activity by stock',
      'Activity by client segment',
      'Overnight reports',
    ]);
    expect(links[0].getAttribute('href')).toBe('/analyst/reports/trade-activity-by-period');
  });
});
