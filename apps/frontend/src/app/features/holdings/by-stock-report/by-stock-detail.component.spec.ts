import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { ByStockDetailComponent } from './by-stock-detail.component';
import { TradeService } from '../../../core/trades/trade.service';
import { Auth } from '../../../core/auth/auth';
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
      imports: [
        ByStockDetailComponent,
        CommonModule,
        MatProgressSpinnerModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        ByStockTradesTable
      ],
      providers: [
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
    it('should extract symbol from route parameter', fakeAsync(() => {
      fixture.detectChanges();
      tick(100);
      
      expect(component['selectedSymbol']()).toBe('AAPL');
    }));

    it('should uppercase the symbol', fakeAsync(() => {
      paramsSubject.next({ symbol: 'aapl' });
      fixture.detectChanges();
      tick(100);
      
      expect(component['selectedSymbol']()).toBe('AAPL');
    }));
  });

  describe('Data Loading', () => {
    it('should load trades for the symbol on init', fakeAsync(() => {
      fixture.detectChanges();
      tick(100);
      
      expect(tradeService.getTradesBySymbol).toHaveBeenCalledWith(1, 'AAPL');
    }));

    it('should store trades in component state', fakeAsync(() => {
      fixture.detectChanges();
      tick(100);
      
      expect(component['trades']().length).toBeGreaterThan(0);
    }));

    it('should set isLoading signal to false after fetch', fakeAsync(() => {
      fixture.detectChanges();
      tick(100);
      
      expect(component['isLoading']()).toBe(false);
    }));
  });

  describe('Error Handling', () => {
    it('should display safe error message on 403 ACCOUNT_ACCESS_DENIED', fakeAsync(() => {
      const error403 = { status: 403, error: { message: 'Forbidden' } };
      tradeService.getTradesBySymbol.mockReturnValueOnce(throwError(() => error403));

      fixture.detectChanges();
      tick(100);
      
      expect(component['error']()?.code).toBe('ACCOUNT_ACCESS_DENIED');
      expect(component['error']()?.message).toBe('You do not have permission to view this account.');
    }));

    it('should show NO_ACCOUNT error when currentAccountId is null', fakeAsync(() => {
      const authMock = { currentAccountId: () => null };
      TestBed.inject(Auth);
      (authService as any) = authMock;
      
      component['loadTrades']('AAPL');
      tick(100);
      
      expect(component['error']()?.code).toBe('NO_ACCOUNT');
    }));

    it('should reload trades on retry', fakeAsync(() => {
      tradeService.getTradesBySymbol.mockClear();
      fixture.detectChanges();
      tick(100);
      
      component['retry']();
      tick(100);
      
      expect(tradeService.getTradesBySymbol).toHaveBeenCalled();
    }));
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
    it('should show empty state when no trades found', fakeAsync(() => {
      tradeService.getTradesBySymbol.mockReturnValueOnce(of([]));
      fixture.detectChanges();
      tick(100);
      
      expect(component['hasNoTrades']()).toBe(true);
    }));
  });
});

// Import vi for mocking (Vitest)
import { vi } from 'vitest';
