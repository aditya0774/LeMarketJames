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

    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.report-card .report-title'));
    expect(links.map((a) => a.textContent)).toEqual([
      'Trade Activity by Period',
      'Activity by Stock',
      'Activity by Client Segment',
      'Overnight Reports',
    ]);
  });
});
