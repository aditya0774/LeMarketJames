import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { OrderService } from '../../core/orders/order.service';
import { Quotes } from '../../core/quotes/quotes';
import { Dashboard } from './dashboard';

const holdings = [
  { symbol: 'AAPL', quantity: 10, averageCost: 100, currentPrice: 120, totalCost: 1000, currentValue: 1200, gainLoss: 200, gainLossPercent: 20 },
  { symbol: 'TSLA', quantity: 2, averageCost: 250, currentPrice: 200, totalCost: 500, currentValue: 400, gainLoss: -100, gainLossPercent: -20 },
];

function quote(symbol: string, price: number) {
  return {
    symbol, name: symbol, price, priceChange: -1, priceChangePercent: -0.4, openPrice: price + 1,
    highPrice: price + 2, lowPrice: price - 2, volume: 1000, marketCap: 1e9, peRatio: 0, dividendYield: 0, lastUpdate: '',
  };
}

function order(orderId: number, orderStatus: string, instrumentId = 1) {
  return {
    orderId, accountId: 7, instrumentId, orderType: 'BUY', quantity: 1, orderStatus,
    submittedAt: `2026-09-0${orderId}T10:00:00`, createdAt: '', updatedAt: '',
  };
}

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let ordersResult: Observable<unknown>;

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(7), logout: async () => {} } },
        { provide: HoldingsService, useValue: { getOwnHoldings: () => of({ success: true, holdings }), error: signal(null) } },
        { provide: OrderService, useValue: { getOrdersByAccountId: () => ordersResult } },
        {
          provide: Quotes,
          useValue: {
            watchQuotes: () => of({ AAPL: quote('AAPL', 227.5), TSLA: quote('TSLA', 248.9) }),
            watchQuote: (symbol: string) => of({ success: true, quote: quote(symbol, 248.9) }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('shows portfolio totals and open order count', async () => {
    ordersResult = of([order(1, 'FILLED'), order(2, 'SUBMITTED', 5), order(3, 'PENDING')]);
    await setup();

    const cards = Array.from(fixture.nativeElement.querySelectorAll('.stat-card .val')).map(
      (el) => (el as HTMLElement).textContent?.trim(),
    );
    // Value 1600, cost 1500 → +$100 (+6.67%); 2 of 3 orders are still open.
    expect(cards).toEqual(['—', '$1,600.00', '+6.67%', '2']);
    expect(text()).toContain('Welcome back, lebron');
    expect(text()).toContain('TSLA');
    expect(text()).toContain('Showing 1–3 of 3 orders');
  });

  it('lists every stock with its live price and a trend graph', async () => {
    ordersResult = of([]);
    await setup();

    const rows = fixture.nativeElement.querySelectorAll('app-market-list tbody tr') as NodeListOf<HTMLElement>;
    expect(rows.length).toBe(6);
    const tsla = Array.from(rows).find((r) => r.textContent?.includes('TSLA'))!;
    expect(tsla.textContent).toContain('$248.90');
    expect(tsla.textContent).toContain('▼');
    // Seeded with [open, price], so the graph has a line straight away.
    expect(tsla.querySelector('.row-chart path')?.getAttribute('d')).toContain('L');
  });

  it('opens the buy/sell popup when a stock is clicked, and closes it again', async () => {
    ordersResult = of([]);
    await setup();

    const rows = fixture.nativeElement.querySelectorAll('app-market-list tbody tr') as NodeListOf<HTMLElement>;
    Array.from(rows).find((r) => r.textContent?.includes('TSLA'))!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role=dialog] h2')?.textContent).toContain('Trade TSLA');

    (fixture.nativeElement.querySelector('.modal-close') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role=dialog]')).toBeNull();
  });

  it('filters orders by status chip', async () => {
    ordersResult = of([order(1, 'FILLED'), order(2, 'REJECTED'), order(3, 'PENDING')]);
    await setup();

    const chips = fixture.nativeElement.querySelectorAll('.chip') as NodeListOf<HTMLButtonElement>;
    chips[3].click(); // Rejected
    fixture.detectChanges();
    await fixture.whenStable();

    const rows = fixture.nativeElement.querySelectorAll('app-orders-panel tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Rejected');
  });

  it('shows the session-expired alert on 401', async () => {
    ordersResult = throwError(() => new HttpErrorResponse({ status: 401 }));
    await setup();

    expect(text()).toContain('Your session has expired');
  });
});
