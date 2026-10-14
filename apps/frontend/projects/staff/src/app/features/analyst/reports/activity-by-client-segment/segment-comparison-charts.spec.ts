import { TestBed } from '@angular/core/testing';
import { SegmentComparisonCharts } from './segment-comparison-charts';
import { SegmentComparisonRow } from '@staff/core/reports/reporting.service';

describe('SegmentComparisonCharts', () => {
  const row = (segment: string, current: number, previous: number): SegmentComparisonRow => ({
    segment,
    current: { segment, activeClients: current, tradeCount: current * 10, totalTradedValue: current * 1250.5 },
    previous: { segment, activeClients: previous, tradeCount: previous * 10, totalTradedValue: previous * 1250.5 },
    activeClients: { absolute: current - previous, percent: previous ? (current - previous) / previous * 100 : null },
    tradeCount: { absolute: (current - previous) * 10, percent: previous ? (current - previous) / previous * 100 : null },
    totalTradedValue: { absolute: (current - previous) * 1250.5, percent: previous ? (current - previous) / previous * 100 : null },
  });

  it('scales both periods against the same maximum and exposes exact values', async () => {
    await TestBed.configureTestingModule({ imports: [SegmentComparisonCharts] }).compileComponents();
    const fixture = TestBed.createComponent(SegmentComparisonCharts);
    fixture.componentRef.setInput('dates', {
      startDate: '2026-09-14', endDate: '2026-10-14',
      comparisonStartDate: '2026-08-14', comparisonEndDate: '2026-09-13',
    });
    fixture.componentRef.setInput('rows', [row('RETAIL', 10, 20), row('ACTIVE_TRADER', 5, 0)]);
    fixture.detectChanges();
    const charts = fixture.nativeElement.querySelectorAll('.metric-chart');
    expect(charts.length).toBe(3);
    const widths = Array.from(charts[0].querySelectorAll('.trend-bar')).map((bar: any) => bar.style.width);
    expect(widths).toEqual(['100%', '50%', '0%', '25%']);
    expect(charts[0].textContent).toContain('-50.00%');
    expect(charts[0].textContent).toContain('N/A');
    expect(charts[2].textContent).toContain('$25,010.00');
    expect(charts[0].querySelector('.period-bar').getAttribute('aria-label')).toBe('Retail, 2026-08-14 to 2026-09-13: 20');
    expect(charts[0].querySelector('.period-bar').getAttribute('tabindex')).toBe('0');
  });

  it('renders zero baselines safely and recomputes the scale when data changes', async () => {
    await TestBed.configureTestingModule({ imports: [SegmentComparisonCharts] }).compileComponents();
    const fixture = TestBed.createComponent(SegmentComparisonCharts);
    fixture.componentRef.setInput('dates', {
      startDate: '2026-09-14', endDate: '2026-10-14',
      comparisonStartDate: '2026-08-14', comparisonEndDate: '2026-09-13',
    });
    fixture.componentRef.setInput('rows', [row('RETAIL', 0, 0)]);
    fixture.detectChanges();
    for (const bar of fixture.nativeElement.querySelectorAll('.trend-bar')) expect(bar.style.width).toBe('0%');
    fixture.componentRef.setInput('rows', [row('RETAIL', 4, 2)]);
    fixture.detectChanges();
    const bars = fixture.nativeElement.querySelectorAll('.metric-chart')[0].querySelectorAll('.trend-bar');
    expect(bars[0].style.width).toBe('50%');
    expect(bars[1].style.width).toBe('100%');
    expect(fixture.nativeElement.textContent).toContain('+100.00%');
  });
});
