import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { ByStockDetailComponent } from './by-stock-detail.component';
import { TradeService } from '../../../core/trades/trade.service';
import { Auth } from '../../../core/auth/auth';
import { HoldingsService } from '../../../core/holdings/holdings.service';
import { TradeDto } from '../../../shared/models/trade.model';
import { ByStockTradesTable } from './by-stock-trades-table';

describe('ByStockDetailComponent', () => {
  let component: ByStockDetailComponent;
  let fixture: ComponentFixture<ByStockDetailComponent>;
  let tradeService: any;
  let router: any;
  let authService: any;
  let paramsSubject: BehaviorSubject<any>;

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
    }
  ];

  beforeEach(async () => {
    paramsSubject = new BehaviorSubject({ symbol: 'AAPL' });
    
    const tradeServiceMock = {
      getTradesBySymbol: vi.fn().mockReturnValue(of(mockTrades))
    };

    const authServiceMock = {
      currentAccountId: () => 1
    };

    const routerMock = {
      navigate: vi.fn().mockResolvedValue(true)
    };

    const activatedRouteMock = {
      params: paramsSubject.asObservable()
    };

    await TestBed.configureTestingModule({
      imports: [ByStockDetailComponent, ByStockTradesTable],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: HoldingsService,
          useValue: {
            getOwnHoldings: () =>
              of({
                success: true,
                holdings: [
                  { symbol: 'AAPL', quantity: 5, averageCost: 150, currentPrice: 160, totalCost: 750, currentValue: 800, gainLoss: 50, gainLossPercent: 6.67 },
                  { symbol: 'MSFT', quantity: 1, averageCost: 400, currentPrice: 410, totalCost: 400, currentValue: 410, gainLoss: 10, gainLossPercent: 2.5 },
                ],
              }),
          },
        },
        { provide: TradeService, useValue: tradeServiceMock },
        { provide: Auth, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ByStockDetailComponent);
    component = fixture.componentInstance;
    tradeService = TestBed.inject(TradeService);
    router = TestBed.inject(Router);
    authService = TestBed.inject(Auth);
  });

  describe('Route Parameter Extraction', () => {
    it('should extract symbol from route parameter', () => {
      fixture.detectChanges();
      
      expect(component['selectedSymbol']()).toBe('AAPL');
    });

    it('should uppercase the symbol', () => {
      paramsSubject.next({ symbol: 'aapl' });
      fixture.detectChanges();
      
      expect(component['selectedSymbol']()).toBe('AAPL');
    });
  });

  describe('Data Loading', () => {
    it('should load trades for the symbol on init', () => {
      fixture.detectChanges();
      
      expect(tradeService.getTradesBySymbol).toHaveBeenCalledWith(1, 'AAPL');
    });

    it('should store trades in component state', () => {
      fixture.detectChanges();
      
      expect(component['trades']().length).toBeGreaterThan(0);
    });

    it('should set isLoading signal to false after fetch', () => {
      fixture.detectChanges();
      
      expect(component['isLoading']()).toBe(false);
    });
  });

  describe('Error Handling', () => {
    it('should display safe error message on 403 ACCOUNT_ACCESS_DENIED', () => {
      const error403 = { status: 403, error: { message: 'Forbidden' } };
      tradeService.getTradesBySymbol.mockReturnValueOnce(throwError(() => error403));

      fixture.detectChanges();
      
      expect(component['error']()?.code).toBe('ACCOUNT_ACCESS_DENIED');
      expect(component['error']()?.message).toBe('You do not have permission to view this account.');
    });

    it('should show NO_ACCOUNT error when currentAccountId is null', () => {
      // Spy on the injected auth service and mock it to return null
      vi.spyOn(authService, 'currentAccountId').mockReturnValueOnce(null);
      
      component['loadTrades']('AAPL');
      
      expect(component['error']()?.code).toBe('NO_ACCOUNT');
    });

    it('should reload trades on retry', () => {
      tradeService.getTradesBySymbol.mockClear();
      fixture.detectChanges();
      
      component['retry']();
      
      expect(tradeService.getTradesBySymbol).toHaveBeenCalled();
    });
  });

  describe('Navigation', () => {
    it('should navigate back to holdings on goBackToHoldings', () => {
      fixture.detectChanges();
      component['goBackToHoldings']();
      
      expect(router.navigate).toHaveBeenCalledWith(['/holdings']);
    });

    it('should have a back button in the template', () => {
      fixture.detectChanges();
      const backButton = fixture.nativeElement.querySelector('.back-button');
      
      expect(backButton).toBeTruthy();
    });
  });

  describe('Empty State', () => {
    it('should show empty state when no trades found', () => {
      tradeService.getTradesBySymbol.mockReturnValueOnce(of([]));
      fixture.detectChanges();
      
      expect(component['hasNoTrades']()).toBe(true);
    });
  });
  describe('Position and actions', () => {
    it('summarises the position from the holdings list', () => {
      fixture.detectChanges();
      const summary: HTMLElement = fixture.nativeElement.querySelector('.position');

      expect(summary.textContent).toContain('Shares you own');
      expect(summary.textContent).toContain('$150.00');
      expect(summary.textContent).toContain('$800.00');
      expect(summary.textContent).toContain('+$50.00');
    });

    it('opens the trade page for this stock', () => {
      fixture.detectChanges();
      const trade = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find(
        (b) => b.textContent?.trim() === 'Trade AAPL',
      )!;
      trade.click();

      expect(router.navigate).toHaveBeenCalledWith(['/trade'], { queryParams: { symbol: 'AAPL' } });
    });

    it('switches between the stocks the user owns', () => {
      fixture.detectChanges();
      const select: HTMLSelectElement = fixture.nativeElement.querySelector('.switch select');
      expect(Array.from(select.options).map((o) => o.value)).toEqual(['AAPL', 'MSFT']);

      select.value = 'MSFT';
      select.dispatchEvent(new Event('change'));

      expect(router.navigate).toHaveBeenCalledWith(['/holdings', 'MSFT']);
    });
  });
});

// Import vi for mocking (Vitest)
import { vi } from 'vitest';
