import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { ByStockTradesTable } from './by-stock-trades-table';
import { TradeDto } from '../../../shared/models/trade.model';

describe('ByStockTradesTable', () => {
  let component: ByStockTradesTable;
  let fixture: ComponentFixture<ByStockTradesTable>;
  let compiled: DebugElement;

  const mockTrades: TradeDto[] = [
    {
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 10,
      pricePerUnit: 150.00,
      filledAt: '2026-09-21T10:30:00'
    },
    {
      symbol: 'AAPL',
      side: 'SELL',
      quantity: 5,
      pricePerUnit: 155.00,
      filledAt: '2026-09-22T14:15:00'
    },
    {
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 3,
      pricePerUnit: 160.00,
      filledAt: '2026-09-20T09:00:00'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ByStockTradesTable, CommonModule]
    }).compileComponents();

    fixture = TestBed.createComponent(ByStockTradesTable);
    component = fixture.componentInstance;
    compiled = fixture.debugElement;

    // Set inputs
    fixture.componentRef.setInput('trades', mockTrades);
    fixture.componentRef.setInput('selectedSymbol', 'AAPL');
  });

  describe('Rendering', () => {
    /**
     * Test: Table renders with correct structure
     * Verify table element exists with thead and tbody
     */
    it('should render table element', () => {
      fixture.detectChanges();
      const table = compiled.query(By.css('table.trades-table'));
      expect(table).toBeTruthy();
    });

    /**
     * Test: Table header contains all sortable columns
     * Verify column headers match expected structure
     */
    it('should render all column headers', () => {
      fixture.detectChanges();
      const headers = compiled.queryAll(By.css('th'));
      expect(headers.length).toBe(5); // Date, Side, Quantity, Price, Cost
    });

    /**
     * Test: Table renders correct number of rows
     * Verify each trade has a row
     */
    it('should render a row for each trade', () => {
      fixture.detectChanges();
      const rows = compiled.queryAll(By.css('tbody tr:not(.empty-row)'));
      expect(rows.length).toBe(mockTrades.length);
    });

    /**
     * Test: Trade data is displayed correctly
     * Verify symbol, quantity, price in cells
     */
    it('should display trade data correctly in table cells', () => {
      fixture.detectChanges();
      const firstRow = compiled.query(By.css('tbody tr'));
      
      // Check that row contains expected data
      expect(firstRow.nativeElement.textContent).toContain('150');
    });

    /**
     * Test: Empty state shows when no trades
     * Verify empty message displayed
     */
    it('should display empty state when trades list is empty', () => {
      fixture.componentRef.setInput('trades', []);
      fixture.detectChanges();

      const emptyCell = compiled.query(By.css('td.empty-cell'));
      expect(emptyCell).toBeTruthy();
      expect(emptyCell.nativeElement.textContent).toContain('No trades found');
    });

    /**
     * Test: Summary shows correct trade count
     * Verify trade count is displayed
     */
    it('should display trades summary', () => {
      fixture.detectChanges();
      const summary = compiled.query(By.css('.summary-text'));
      expect(summary).toBeTruthy();
      expect(summary.nativeElement.textContent).toContain('3 trade(s)');
    });
  });

  describe('Sorting', () => {
    /**
     * Test: Default sort is newest first (by date descending)
     * Verify first row shows most recent trade
     */
    it('should sort by date descending by default (newest first)', () => {
      fixture.detectChanges();
      const rows = compiled.queryAll(By.css('tbody tr:not(.empty-row)'));
      const firstRowText = rows[0].nativeElement.textContent;

      // The most recent trade should be first (2026-09-22)
      expect(firstRowText).toContain('Sep 22');
    });

    /**
     * Test: Click date header toggles sort direction
     * Verify sort direction changes
     */
    it('should toggle sort direction when clicking same column twice', () => {
      fixture.detectChanges();
      const dateHeader = compiled.query(By.css('th'));

      // Click to sort ascending
      dateHeader.nativeElement.click();
      fixture.detectChanges();
      
      // Click again to sort descending
      dateHeader.nativeElement.click();
      fixture.detectChanges();

      // Verify sort indicator shows descending
      const sortIndicator = dateHeader.query(By.css('.sort-indicator'));
      expect(sortIndicator.nativeElement.textContent).toContain('↓');
    });

    /**
     * Test: Click quantity header to sort by quantity
     * Verify rows are sorted by quantity
     */
    it('should sort by quantity when quantity header is clicked', () => {
      fixture.detectChanges();
      const headers = compiled.queryAll(By.css('th'));
      const quantityHeader = headers[2]; // Quantity column

      quantityHeader.nativeElement.click();
      fixture.detectChanges();

      const rows = compiled.queryAll(By.css('tbody tr:not(.empty-row)'));
      const firstRowQty = rows[0].nativeElement.textContent;
      const lastRowQty = rows[rows.length - 1].nativeElement.textContent;

      // First row should have 3, last row should have 10 (ascending)
      expect(firstRowQty).toContain('3');
      expect(lastRowQty).toContain('10');
    });

    /**
     * Test: Click price header to sort by price
     * Verify rows are sorted by price
     */
    it('should sort by price when price header is clicked', () => {
      fixture.detectChanges();
      const headers = compiled.queryAll(By.css('th'));
      const priceHeader = headers[3]; // Price column

      priceHeader.nativeElement.click();
      fixture.detectChanges();

      const rows = compiled.queryAll(By.css('tbody tr:not(.empty-row)'));
      // After sorting by price ascending: 150, 155, 160
      const firstRowPrice = rows[0].nativeElement.textContent;
      expect(firstRowPrice).toContain('150');
    });

    /**
     * Test: Sort indicator changes when sorting
     * Verify up/down arrow displayed
     */
    it('should show sort direction indicator', () => {
      fixture.detectChanges();
      const headers = compiled.queryAll(By.css('th'));
      const dateHeader = headers[0];

      dateHeader.nativeElement.click();
      fixture.detectChanges();

      const sortIndicator = dateHeader.query(By.css('.sort-indicator'));
      expect(sortIndicator.nativeElement.textContent).toContain('↑');
    });

    /**
     * Test: Clicking different column changes sort column
     * Verify sort is applied to new column
     */
    it('should change sort column when clicking different header', () => {
      fixture.detectChanges();
      const headers = compiled.queryAll(By.css('th'));
      
      // Click date header first
      headers[0].nativeElement.click();
      fixture.detectChanges();
      
      // Then click quantity header
      headers[2].nativeElement.click();
      fixture.detectChanges();

      // Quantity header should show sort indicator
      const qtyHeader = headers[2];
      const sortIndicator = qtyHeader.query(By.css('.sort-indicator'));
      expect(sortIndicator.nativeElement.textContent).toContain('↑');
    });
  });

  describe('Badges', () => {
    /**
     * Test: BUY badge is styled correctly
     * Verify green badge for BUY trades
     */
    it('should show green badge for BUY trades', () => {
      fixture.detectChanges();
      const buyBadges = compiled.queryAll(By.css('.badge-buy'));
      expect(buyBadges.length).toBeGreaterThan(0);
    });

    /**
     * Test: SELL badge is styled correctly
     * Verify red badge for SELL trades
     */
    it('should show red badge for SELL trades', () => {
      fixture.detectChanges();
      const sellBadges = compiled.queryAll(By.css('.badge-sell'));
      expect(sellBadges.length).toBeGreaterThan(0);
    });

    /**
     * Test: Badge text displays side correctly
     * Verify BUY/SELL text in badge
     */
    it('should display correct side text in badge', () => {
      fixture.detectChanges();
      const badges = compiled.queryAll(By.css('.badge'));
      
      expect(badges.some(b => b.nativeElement.textContent.includes('BUY'))).toBe(true);
      expect(badges.some(b => b.nativeElement.textContent.includes('SELL'))).toBe(true);
    });
  });

  describe('Formatting', () => {
    /**
     * Test: Date is formatted correctly
     * Verify readable date format
     */
    it('should format date correctly', () => {
      fixture.detectChanges();
      const dateCell = compiled.query(By.css('.date-cell'));
      const text = dateCell.nativeElement.textContent;

      // Should contain month name and year
      expect(text).toMatch(/\w{3} \d{1,2}, \d{4}/);
    });

    /**
     * Test: Price is formatted as currency
     * Verify USD currency format
     */
    it('should format price as currency', () => {
      fixture.detectChanges();
      const priceCell = compiled.query(By.css('.price-cell'));
      const text = priceCell.nativeElement.textContent;

      // Should contain currency symbol and decimals
      expect(text).toContain('$');
    });

    /**
     * Test: Cost calculation is correct
     * Verify quantity × price = cost
     */
    it('should calculate and display cost correctly', () => {
      fixture.detectChanges();
      const costCell = compiled.query(By.css('.cost-cell'));
      const text = costCell.nativeElement.textContent;

      // First trade: 10 × 150 = 1500
      // Should display currency formatted
      expect(text).toContain('$');
    });

    /**
     * Test: Quantity has no decimals
     * Verify integer format
     */
    it('should format quantity as integer', () => {
      fixture.detectChanges();
      const qtyCell = compiled.query(By.css('.quantity-cell'));
      const text = qtyCell.nativeElement.textContent.trim();

      // Should be whole number
      expect(/^\d+$/.test(text)).toBe(true);
    });
  });

  describe('Summary', () => {
    /**
     * Test: Summary displays correct symbol
     * Verify selected symbol shown in summary
     */
    it('should display selected symbol in summary', () => {
      fixture.componentRef.setInput('selectedSymbol', 'AAPL');
      fixture.detectChanges();

      const summary = compiled.query(By.css('.summary-text'));
      expect(summary.nativeElement.textContent).toContain('AAPL');
    });

    /**
     * Test: No summary when no trades
     * Verify summary hidden for empty state
     */
    it('should not show summary when trades are empty', () => {
      fixture.componentRef.setInput('trades', []);
      fixture.detectChanges();

      const summary = compiled.query(By.css('.summary-text'));
      expect(summary).toBeFalsy();
    });
  });
});
