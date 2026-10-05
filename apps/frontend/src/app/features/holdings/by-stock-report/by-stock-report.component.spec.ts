import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { vi, expect } from 'vitest';

import { ByStockReportComponent } from './by-stock-report.component';
import { TradeService } from '../../../core/trades/trade.service';
import { Auth } from '../../../core/auth/auth';
import { TradeDto } from '../../../shared/models/trade.model';

describe('ByStockReportComponent', () => {
  let component: ByStockReportComponent;
  let fixture: ComponentFixture<ByStockReportComponent>;
  let tradeServiceMock: any;
  let authServiceMock: any;

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
      symbol: 'GOOGL',
      side: 'BUY',
      quantity: 2,
      pricePerUnit: 140.50,
      filledAt: '2026-09-20T09:00:00'
    }
  ];

  beforeEach(async () => {
    // Mock TradeService
    tradeServiceMock = {
      getTrades: (accountId: number) => of(mockTrades),
      getTradesBySymbol: (accountId: number, symbol: string) =>
        of(mockTrades.filter(t => t.symbol === symbol))
    };

    // Mock Auth service
    authServiceMock = {
      currentAccountId: signal<number | null>(1),
      currentUser: signal<string | null>('testuser')
    };

    await TestBed.configureTestingModule({
      imports: [
        ByStockReportComponent,
        CommonModule,
        MatProgressSpinnerModule,
        MatCardModule,
        MatSelectModule,
        MatFormFieldModule,
        MatButtonModule,
        MatIconModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: Auth, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ByStockReportComponent);
    component = fixture.componentInstance;
  });

  describe('Initialization', () => {
    /**
     * Test: Component loads trades on init
     * Verify getOwnTrades is called
     */
    it('should load trades on init', () => {
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of(mockTrades));
      component.ngOnInit();
      expect(tradeServiceMock.getTrades).toHaveBeenCalledWith(1);
    });

    /**
     * Test: Component extracts account ID from Auth service
     * Verify currentAccountId is used
     */
    it('should get accountId from Auth service', () => {
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of(mockTrades));
      component.ngOnInit();
      expect(tradeServiceMock.getTrades).toHaveBeenCalledWith(
        authServiceMock.currentAccountId()
      );
    });

    /**
     * Test: No account ID available
     * Verify error state is set
     */
    it('should set error when no accountId is available', () => {
      authServiceMock.currentAccountId.set(null);
      component.ngOnInit();
      expect(component['error']()).toBeTruthy();
      expect(component['error']()?.code).toBe('NO_ACCOUNT');
    });
  });

  describe('Success State: Trades Loaded', () => {
    /**
     * Test: Trades loaded successfully
     * Verify trades are stored and symbols extracted
     */
    it('should load trades and extract unique symbols', () => {
      component.ngOnInit();
      fixture.detectChanges();

      expect(component['trades']().length).toBe(3);
      expect(component['symbols']()).toContain('AAPL');
      expect(component['symbols']()).toContain('GOOGL');
    });

    /**
     * Test: Auto-select first symbol
     * Verify selectedSymbol is set to first symbol
     */
    it('should auto-select first symbol when trades load', () => {
      component.ngOnInit();
      fixture.detectChanges();

      const firstSymbol = component['symbols']()[0];
      expect(component['selectedSymbol']()).toBe(firstSymbol);
    });

    /**
     * Test: Filter trades by selected symbol
     * Verify filteredTrades returns only selected symbol's trades
     */
    it('should filter trades by selected symbol', () => {
      component.ngOnInit();
      fixture.detectChanges();

      component['selectedSymbol'].set('AAPL');
      fixture.detectChanges();

      const filtered = component['filteredTrades']();
      expect(filtered.length).toBe(2);
      expect(filtered.every(t => t.symbol === 'AAPL')).toBe(true);
    });

    /**
     * Test: Change selected symbol
     * Verify onSymbolChange updates the selected symbol
     */
    it('should update selectedSymbol when onSymbolChange is called', () => {
      component.ngOnInit();
      fixture.detectChanges();

      component.onSymbolChange('GOOGL');
      expect(component['selectedSymbol']()).toBe('GOOGL');
    });

    /**
     * Test: Filtered trades are empty for symbol with no trades
     * Verify empty state when no trades for selected symbol
     */
    it('should return empty array when filtering non-existent symbol', () => {
      component.ngOnInit();
      fixture.detectChanges();
      
      // Select a symbol that exists to verify filtering works
      component.onSymbolChange('GOOGL');
      fixture.detectChanges();
      expect(component['filteredTrades']().length).toBe(1);
      
      // Now select a non-existent symbol
      component['selectedSymbol'].set('NONEXISTENT');
      fixture.detectChanges();
      expect(component['filteredTrades']().length).toBe(0);
    });
  });

  describe('Loading State', () => {
    /**
     * Test: Loading state is set during fetch
     * Verify isLoading signal reflects loading status
     */
    it('should set isLoading during trade fetch', async () => {
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of(mockTrades));
      component.ngOnInit();

      // Check isLoading becomes false after subscribe
      await new Promise(resolve => setTimeout(resolve, 100));
      expect(component['isLoading']()).toBe(false);
    });

    /**
     * Test: Loading state clears after data arrives
     * Verify error state is cleared
     */
    it('should clear error when trades load successfully', () => {
      component.ngOnInit();
      fixture.detectChanges();

      expect(component['error']()).toBeNull();
    });
  });

  describe('Error Handling', () => {
    /**
     * AC1: Unauthorized data access prevented
     * Test: 403 error → safe error message displayed
     */
    it('should display safe error message on 403 ACCOUNT_ACCESS_DENIED', () => {
      const mockError = { status: 403, error: { message: 'Forbidden' } };
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(
        throwError(() => mockError)
      );

      component.ngOnInit();
      fixture.detectChanges();

      const error = component['error']();
      expect(error).toBeTruthy();
      expect(error?.code).toBe('ACCOUNT_ACCESS_DENIED');
      expect(error?.message).toBe('You do not have permission to view this account.');
    });

    /**
     * Test: 400 Bad Request error
     * Verify error message is shown
     */
    it('should handle 400 Bad Request error', () => {
      const mockError = { status: 400, error: { message: 'Invalid request' } };
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(
        throwError(() => mockError)
      );

      component.ngOnInit();
      fixture.detectChanges();

      const error = component['error']();
      expect(error?.code).toBe('BAD_REQUEST');
    });

    /**
     * Test: 500+ Server error
     * Verify generic error message
     */
    it('should handle 500 Server error', () => {
      const mockError = { status: 500, error: { message: 'Server error' } };
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(
        throwError(() => mockError)
      );

      component.ngOnInit();
      fixture.detectChanges();

      const error = component['error']();
      expect(error?.code).toBe('SERVER_ERROR');
      expect(error?.message).toBe('Server error. Please try again later.');
    });

    /**
     * Test: Network error
     * Verify network error handling
     */
    it('should handle network error', () => {
      const mockError = { status: 0, message: 'Network error' };
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(
        throwError(() => mockError)
      );

      component.ngOnInit();
      fixture.detectChanges();

      const error = component['error']();
      expect(error?.code).toBe('NETWORK_ERROR');
    });

    /**
     * Test: Retry after error
     * Verify retry reloads trades
     */
    it('should retry loading trades after error', () => {
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of(mockTrades));
      
      component.ngOnInit();
      fixture.detectChanges();
      
      // Clear the spy call count
      vi.clearAllMocks();
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of(mockTrades));
      
      component.retry();
      expect(tradeServiceMock.getTrades).toHaveBeenCalled();
    });
  });

  describe('Empty State', () => {
    /**
     * Test: No trades loaded
     * Verify empty state indicator
     */
    it('should indicate hasNoTrades when trades array is empty', () => {
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of([]));
      component.ngOnInit();
      fixture.detectChanges();

      expect(component['hasNoTrades']()).toBe(true);
    });

    /**
     * Test: No trades → empty state message shown
     */
    it('should show empty state when no trades exist', () => {
      vi.spyOn(tradeServiceMock, 'getTrades').mockReturnValue(of([]));
      component.ngOnInit();
      fixture.detectChanges();

      expect(component['trades']().length).toBe(0);
      expect(component['symbols']().length).toBe(0);
    });
  });
});
