import { TestBed } from '@angular/core/testing';
import { ByStockAggregateTable } from './by-stock-aggregate-table';
import { TradesByStockRow } from '../../../core/reports/reporting.service';

describe('ByStockAggregateTable', () => {
  let component: ByStockAggregateTable;
  let fixture: any;

  // Real seed_active persona trades aggregated from database/schema/011_seed_test_data.sql
  const mockTradesData: TradesByStockRow[] = [
    { symbol: 'AAPL', totalQuantity: 20, totalGrossAmount: 6950.00, buyCount: 2, sellCount: 1 }, // Highest gross
    { symbol: 'MSFT', totalQuantity: 6, totalGrossAmount: 5820.00, buyCount: 1, sellCount: 1 },
    { symbol: 'V', totalQuantity: 0, totalGrossAmount: 5250.00, buyCount: 1, sellCount: 1 }, // Closed position
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ByStockAggregateTable],
    }).compileComponents();

    fixture = TestBed.createComponent(ByStockAggregateTable);
    component = fixture.componentInstance;
  });

  describe('Display', () => {
    it('renders empty state when no data', () => {
      fixture.componentRef.setInput('tradesData', []);
      fixture.detectChanges();

      const emptyState = fixture.nativeElement.querySelector('.empty-state');
      expect(emptyState).toBeTruthy();
      expect(emptyState.textContent).toContain('No trading activity');
    });

    it('renders table with data', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      const table = fixture.nativeElement.querySelector('table');
      expect(table).toBeTruthy();

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(3);
    });

    it('displays correct columns', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      const headerTexts = Array.from(headers).map((h: any) => h.textContent.trim());

      expect(headerTexts.some((text: string) => text.includes('Symbol'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Total Quantity'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Total Gross Amount'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Buy Count'))).toBe(true);
      expect(headerTexts.some((text: string) => text.includes('Sell Count'))).toBe(true);
    });

    it('displays correct data in cells', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      const firstRow = rows[0];

      expect(firstRow.querySelector('.symbol').textContent).toContain('AAPL');
      expect(firstRow.textContent).toContain('20'); // totalQuantity
      expect(firstRow.textContent).toContain('6950'); // totalGrossAmount (formatted as $6,950.00)
      expect(firstRow.textContent).toContain('2'); // buyCount
      expect(firstRow.textContent).toContain('1'); // sellCount
    });
  });

  describe('Sorting', () => {
    it('defaults to sorting by gross amount descending', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].symbol).toBe('AAPL'); // Highest gross amount ($6,950.00)
      expect(sortedData[1].symbol).toBe('MSFT'); // $5,820.00
      expect(sortedData[2].symbol).toBe('V');    // $5,250.00 (closed position)
    });

    it('sorts by symbol ascending when clicked', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('symbol');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].symbol).toBe('AAPL');
      expect(sortedData[1].symbol).toBe('MSFT');
      expect(sortedData[2].symbol).toBe('TSLA');
    });

    it('toggles sort direction on same column', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('symbol');
      fixture.detectChanges();
      let sortedData = component['sortedData']();
      expect(sortedData[0].symbol).toBe('AAPL'); // ascending

      component.toggleSort('symbol');
      fixture.detectChanges();
      sortedData = component['sortedData']();
      expect(sortedData[0].symbol).toBe('TSLA'); // descending
    });

    it('sorts by totalQuantity', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('totalQuantity');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].totalQuantity).toBe(100);
      expect(sortedData[1].totalQuantity).toBe(150);
      expect(sortedData[2].totalQuantity).toBe(200);
    });

    it('sorts by buyCount', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('buyCount');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].buyCount).toBe(5);
      expect(sortedData[1].buyCount).toBe(8);
      expect(sortedData[2].buyCount).toBe(10);
    });

    it('sorts by sellCount', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('sellCount');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].sellCount).toBe(2);
      expect(sortedData[1].sellCount).toBe(3);
      expect(sortedData[2].sellCount).toBe(5);
    });

    it('sorts by totalGrossAmount descending by default', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('totalGrossAmount');
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].totalGrossAmount).toBe(50200.00); // highest first (descending)
      expect(sortedData[1].totalGrossAmount).toBe(22500.75);
      expect(sortedData[2].totalGrossAmount).toBe(15000.00);
    });
  });

  describe('Sort Indicator', () => {
    it('shows ascending indicator for sorted column', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('symbol');
      fixture.detectChanges();

      const indicator = component.getSortIndicator('symbol');
      expect(indicator).toBe('↑');
    });

    it('shows descending indicator when toggled', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      component.toggleSort('symbol');
      component.toggleSort('symbol');
      fixture.detectChanges();

      const indicator = component.getSortIndicator('symbol');
      expect(indicator).toBe('↓');
    });

    it('shows empty string for unsorted columns', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      const indicator = component.getSortIndicator('buyCount');
      expect(indicator).toBe('');
    });
  });
});
