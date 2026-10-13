import { TestBed } from '@angular/core/testing';

import { SegmentActivityRow } from '../../../../core/reports/reporting.service';
import { ByClientSegmentAggregateTable } from './by-client-segment-aggregate-table';

describe('ByClientSegmentAggregateTable', () => {
  let component: ByClientSegmentAggregateTable;
  let fixture: any;

  const mockData: SegmentActivityRow[] = [
    {
      segment: 'RETAIL',
      activeClients: 42,
      tradeCount: 180,
      totalTradedValue: 215090,
    },
    {
      segment: 'ACTIVE_TRADER',
      activeClients: 12,
      tradeCount: 126,
      totalTradedValue: 341250.5,
    },
    {
      segment: 'HIGH_NET_WORTH',
      activeClients: 5,
      tradeCount: 31,
      totalTradedValue: 510000,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ByClientSegmentAggregateTable],
    }).compileComponents();

    fixture = TestBed.createComponent(ByClientSegmentAggregateTable);
    component = fixture.componentInstance;
  });

  describe('Display', () => {
    it('renders empty state when no data', () => {
      fixture.componentRef.setInput('reportData', []);
      fixture.detectChanges();

      const emptyState = fixture.nativeElement.querySelector('.empty-state');
      expect(emptyState).toBeTruthy();
      expect(emptyState.textContent).toContain('No trading activity');
    });

    it('renders table with data', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      const table = fixture.nativeElement.querySelector('table');
      expect(table).toBeTruthy();

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(3);
    });

    it('displays required columns', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      const headerTexts = Array.from(headers).map((h: any) => h.textContent.trim());

      expect(headerTexts.some((text: string) => text.includes('Segment'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Active Clients'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Trade Count'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Total Traded Value'))).toBe(true);
    });
  });

  describe('Sorting', () => {
    it('defaults to sorting by segment name ascending', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].segment).toBe('ACTIVE_TRADER');
      expect(sortedData[1].segment).toBe('HIGH_NET_WORTH');
      expect(sortedData[2].segment).toBe('RETAIL');
    });

    it('toggles segment sort direction when clicking same column', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      component.toggleSort('segment');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].segment).toBe('RETAIL');
      expect(sortedData[2].segment).toBe('ACTIVE_TRADER');
    });

    it('sorts numeric columns ascending on first click', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      component.toggleSort('tradeCount');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].tradeCount).toBe(31);
      expect(sortedData[2].tradeCount).toBe(180);
    });

    it('toggles numeric sort direction on second click', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      component.toggleSort('activeClients');
      component.toggleSort('activeClients');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].activeClients).toBe(42);
      expect(sortedData[2].activeClients).toBe(5);
    });
  });

  describe('Sort Indicator', () => {
    it('shows indicator for active sort column', () => {
      fixture.componentRef.setInput('reportData', mockData);
      fixture.detectChanges();

      expect(component.getSortIndicator('segment')).toBe('↑');
      expect(component.getSortIndicator('tradeCount')).toBe('');
    });
  });
});
