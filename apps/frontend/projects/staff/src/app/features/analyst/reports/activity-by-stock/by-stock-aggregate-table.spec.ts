import { TestBed } from '@angular/core/testing';
import { ByStockAggregateTable } from './by-stock-aggregate-table';
import { TradesByStockRow } from '../../../core/reports/reporting.service';

describe('ByStockAggregateTable', () => {
  let component: ByStockAggregateTable;
  let fixture: any;

  const mockTradesData: TradesByStockRow[] = [
    { symbol: 'AAPL', totalQuantity: 150, totalGrossAmount: 22500.75, buyCount: 8, sellCount: 5 },
    { symbol: 'MSFT', totalQuantity: 200, totalGrossAmount: 50200.00, buyCount: 10, sellCount: 3 },
    { symbol: 'TSLA', totalQuantity: 100, totalGrossAmount: 15000.00, buyCount: 5, sellCount: 2 },
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
      expect(firstRow.textContent).toContain('150'); // totalQuantity
      expect(firstRow.textContent).toContain('22500.75'); // totalGrossAmount
      expect(firstRow.textContent).toContain('8'); // buyCount
      expect(firstRow.textContent).toContain('5'); // sellCount
    });
  });

  describe('Sorting', () => {
    it('defaults to sorting by gross amount descending', () => {
      fixture.componentRef.setInput('tradesData', mockTradesData);
      fixture.detectChanges();

      const sortedData = component['sortedData']();
      expect(sortedData[0].symbol).toBe('MSFT'); // Highest gross amount
      expect(sortedData[1].symbol).toBe('AAPL');
      expect(sortedData[2].symbol).toBe('TSLA');
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
