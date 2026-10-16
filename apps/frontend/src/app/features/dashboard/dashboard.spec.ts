import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { OrderResponse, OrdersStreamSnapshot, OrderService } from '../../core/orders/order.service';
import { BalanceService } from '../../core/orders/orders.service';
import { Quotes } from '../../core/quotes/quotes';
import { loadTestInstruments } from '../../core/market/instrument-catalog.testing';
import { Dashboard } from './dashboard';

const holdings = [
  { symbol: 'TSLA', quantity: 2, averageCost: 250, currentPrice: 200, totalCost: 500, currentValue: 400, gainLoss: -100, gainLossPercent: -20 },
  { symbol: 'AAPL', quantity: 10, averageCost: 100, currentPrice: 120, totalCost: 1000, currentValue: 1200, gainLoss: 200, gainLossPercent: 20 },
];

function quote(symbol: string, price: number, priceChangePercent: number) {
  return {
    symbol, name: symbol, price, priceChange: priceChangePercent, priceChangePercent, openPrice: price,
    highPrice: price + 2, lowPrice: price - 2, volume: 1000, marketCap: 1e9, peRatio: 0, dividendYield: 0, lastUpdate: '',
  };
}

function order(orderId: number, orderStatus: OrderResponse['orderStatus']): OrderResponse {
  return {
    success: true, orderId, accountId: 7, instrumentId: 1, orderType: 'BUY', quantity: 1, orderStatus,
    submittedAt: '2026-09-01T10:00:00', createdAt: '', updatedAt: '',
  };
}

describe('Dashboard (Overview)', () => {
  let fixture: ComponentFixture<Dashboard>;
  let allOrdersResult: Observable<unknown>;
  let holdingsResult: Observable<unknown>;
  let balanceCalls: number;
  let holdingsCalls: number;
  let liveOrders: Subject<OrdersStreamSnapshot>;

  async function setup(balance: unknown = { success: true, balance: { buyingPower: 10000 } }) {
    balanceCalls = 0;
    holdingsCalls = 0;
    liveOrders = new Subject<OrdersStreamSnapshot>();
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(7) } },
        {
          provide: HoldingsService,
          useValue: {
            getOwnHoldings: () => {
              holdingsCalls++;
              return holdingsResult;
            },
            error: signal(null),
          },
        },
        {
          provide: BalanceService,
          useValue: {
            getBalance: () => {
              balanceCalls++;
              return balance instanceof Error ? Promise.reject(balance) : Promise.resolve(balance);
            },
          },
        },
        {
          provide: OrderService,
          useValue: { getAllOrdersByAccountId: () => allOrdersResult, watchOrderStatusStream: () => liveOrders.asObservable() },
        },
        {
          provide: Quotes,
          useValue: {
            watchQuotes: () => of({ AAPL: quote('AAPL', 227.5, 0.8), TSLA: quote('TSLA', 248.9, -3.1), NVDA: quote('NVDA', 176, 1.2) }),
          },
        },
      ],
    }).compileComponents();
    await loadTestInstruments();

    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';

  beforeEach(() => {
    allOrdersResult = of([order(1, 'FILLED'), order(2, 'SUBMITTED'), order(3, 'PENDING')]);
    holdingsResult = of({ success: true, holdings });
  });

  it('shows the four headline figures, open orders last', async () => {
    await setup();

    const values = Array.from(el().querySelectorAll('.stat-card .val')).map((v) => v.textContent?.trim());
    // Value 1600, cost 1500 → +$100 (+6.67%); 2 of 3 orders are still open.
    expect(values).toEqual(['$1,600.00', '+$100.00', '$10,000.00', '2']);
    expect(text()).toContain('6.67%');
    expect(text()).toContain('Welcome back, lebron');
  });

  it('shows "—" for buying power when the balance is unavailable', async () => {
    await setup({ success: false });

    expect(el().querySelectorAll('.stat-card .val')[2].textContent?.trim()).toBe('—');
    expect(text()).toContain('Balance unavailable');
  });

  it('splits what the user owns, largest first', async () => {
    await setup();

    const rows = Array.from(el().querySelectorAll('app-holdings-split li')).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(rows[0]).toContain('AAPL');
    expect(rows[0]).toContain('75%');
    expect(rows[1]).toContain('TSLA');
    expect(el().querySelector('app-holdings-split svg')?.getAttribute('aria-label')).toBe('Portfolio split: AAPL 75%, TSLA 25%');
  });

  it('lists the biggest movers either way, largest first', async () => {
    await setup();

    const symbols = Array.from(el().querySelectorAll('app-movers .sym')).map((s) => s.textContent?.trim());
    expect(symbols).toEqual(['TSLA', 'NVDA', 'AAPL']);
  });

  it('loads the largest holding into quick trade, and any stock the user selects', async () => {
    await setup();
    const select = () => el().querySelector('app-quick-trade select') as HTMLSelectElement;
    expect(select().value).toBe('AAPL');

    (el().querySelector('app-movers button[aria-label="Load TSLA into quick trade"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(select().value).toBe('TSLA');
    expect(el().querySelector('app-quick-trade')?.textContent).toContain('$248.90');
    expect(el().querySelector('app-quick-trade')?.textContent).toContain('You own 2 shares of TSLA.');
  });

  it('reloads holdings and balance when an order changes status', async () => {
    await setup();
    const before = { holdings: holdingsCalls, balance: balanceCalls };

    liveOrders.next({ connection: 'live', event: { orderId: 2, accountId: 7, from: 'SUBMITTED', to: 'FILLED', occurredAt: '' } });
    await fixture.whenStable();

    expect(holdingsCalls).toBeGreaterThan(before.holdings);
    expect(balanceCalls).toBeGreaterThan(before.balance);
  });

  it('says so when there is nothing owned yet', async () => {
    holdingsResult = of({ success: true, holdings: [] });
    await setup();

    expect(el().querySelector('app-holdings-split')?.textContent).toContain('No stocks yet');
  });

  it('shows the session-expired alert on 401', async () => {
    holdingsResult = throwError(() => new HttpErrorResponse({ status: 401 }));
    await setup();

    expect(text()).toContain('Your session has expired');
    expect(liveOrders.observed).toBe(false);
  });
});
