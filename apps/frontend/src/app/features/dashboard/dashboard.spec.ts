import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { OrderResponse, OrdersStreamSnapshot, OrderService } from '../../core/orders/order.service';
import { Quotes } from '../../core/quotes/quotes';
import { Dashboard } from './dashboard';
import { TEST_INSTRUMENTS, loadTestInstruments } from '../../core/market/instrument-catalog.testing';

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

function order(orderId: number, orderStatus: OrderResponse['orderStatus'], instrumentId = 1): OrderResponse {
  const day = String(((orderId - 1) % 28) + 1).padStart(2, '0');
  return {
    success: true,
    orderId, accountId: 7, instrumentId, orderType: 'BUY', quantity: 1, orderStatus,
    submittedAt: `2026-09-${day}T10:00:00`, createdAt: '', updatedAt: '',
  };
}

function rowTextCells(row: Element): string[] {
  return Array.from(row.querySelectorAll('td')).map(cell => (cell.textContent ?? '').trim());
}

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let allOrdersResult: Observable<unknown>;
  let historyPageResult: Observable<unknown>;
  let requestedHistoryRequests: Array<{ page: number; size: number; filter: unknown }>;
  let liveOrders: Subject<OrdersStreamSnapshot>;

  async function setup() {
    requestedHistoryRequests = [];
    liveOrders = new Subject<OrdersStreamSnapshot>();
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(7), logout: async () => {} } },
        { provide: HoldingsService, useValue: { getOwnHoldings: () => of({ success: true, holdings }), error: signal(null) } },
        {
          provide: OrderService,
          useValue: {
            getAllOrdersByAccountId: () => allOrdersResult,
            getOrdersByAccountIdPage: (_accountId: number, request: { page: number; size: number }, filter: unknown) => {
              requestedHistoryRequests.push({ page: request.page, size: request.size, filter });
              return historyPageResult;
            },
            watchOrderStatusStream: () => liveOrders.asObservable(),
          },
        },
        {
          provide: Quotes,
          useValue: {
            watchQuotes: () => of({ AAPL: quote('AAPL', 227.5), TSLA: quote('TSLA', 248.9) }),
            watchQuote: (symbol: string) => of({ success: true, quote: quote(symbol, 248.9) }),
          },
        },
      ],
    }).compileComponents();
    await loadTestInstruments();

    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    // Quote polling starts once the (already loaded) stock list resolves, a microtask later.
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('refreshes history after an open order fills via live updates', async () => {
    allOrdersResult = of([order(1, 'SUBMITTED')]);
    historyPageResult = of([order(1, 'SUBMITTED')]);
    await setup();

    allOrdersResult = of([order(1, 'FILLED')]);
    historyPageResult = of([order(1, 'FILLED')]);
    const before = requestedHistoryRequests.length;
    liveOrders.next({
      connection: 'live',
      event: { orderId: 1, accountId: 7, from: 'SUBMITTED', to: 'FILLED', occurredAt: '2026-09-01T10:00:00Z' },
    });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(requestedHistoryRequests.length).toBeGreaterThan(before); // SSE status events now refresh both orders and history.
    expect(text()).toContain('Filled');
  });

  it('queues an orders refresh when a live event arrives during an in-flight orders request', async () => {
    const first = new Subject<OrderResponse[]>();
    allOrdersResult = first;
    historyPageResult = of([order(1, 'SUBMITTED')]);
    await setup();

    // The queued follow-up request should read this newer state once the first request completes.
    allOrdersResult = of([order(1, 'FILLED')]);
    historyPageResult = of([order(1, 'FILLED')]);
    liveOrders.next({
      connection: 'live',
      event: { orderId: 1, accountId: 7, from: 'SUBMITTED', to: 'FILLED', occurredAt: '2026-09-01T10:00:00Z' },
    });
    first.next([order(1, 'SUBMITTED')]);
    first.complete();

    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Filled');
  });

  it('shows portfolio totals and open order count', async () => {
    allOrdersResult = of([order(1, 'FILLED'), order(2, 'SUBMITTED', 5), order(3, 'PENDING')]);
    historyPageResult = of([order(1, 'FILLED'), order(2, 'SUBMITTED', 5), order(3, 'PENDING')]);
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
    allOrdersResult = of([]);
    historyPageResult = of([]);
    await setup();

    const rows = fixture.nativeElement.querySelectorAll('app-market-list tbody tr') as NodeListOf<HTMLElement>;
    expect(Array.from(rows)).toHaveLength(TEST_INSTRUMENTS.length);
    const tsla = Array.from(rows).find((r) => r.textContent?.includes('TSLA'))!;
    expect(tsla.textContent).toContain('$248.90');
    expect(tsla.textContent).toContain('▼');
    // Seeded with [open, price], so the graph has a line straight away.
    expect(tsla.querySelector('.row-chart path')?.getAttribute('d')).toContain('L');
  });

  it('opens the buy/sell popup when a stock is clicked, and closes it again', async () => {
    allOrdersResult = of([]);
    historyPageResult = of([]);
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
    allOrdersResult = of([order(1, 'FILLED'), order(2, 'REJECTED'), order(3, 'PENDING')]);
    historyPageResult = of([order(1, 'FILLED'), order(2, 'REJECTED'), order(3, 'PENDING')]);
    await setup();

    const chips = fixture.nativeElement.querySelectorAll('.chip') as NodeListOf<HTMLButtonElement>;
    chips[3].click(); // Rejected
    fixture.detectChanges();
    await fixture.whenStable();

    const rows = fixture.nativeElement.querySelectorAll('app-orders-panel tbody tr');
    expect(Array.from(rows)).toHaveLength(1);
    expect(rows[0].textContent).toContain('Rejected');
  });

  it('shows the session-expired alert on 401', async () => {
    allOrdersResult = of([]);
    historyPageResult = throwError(() => new HttpErrorResponse({ status: 401 }));
    await setup();

    expect(text()).toContain('Your session has expired');
  });
  it('requests the local period, cancels stale requests, and clears without parameters', async () => {
    allOrdersResult = of([order(1, 'SUBMITTED')]);
    historyPageResult = of([order(1, 'SUBMITTED')]);
    await setup();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('app-orders-panel input');
    const previous = new Subject<unknown>();
    historyPageResult = previous;
    input.value = '2026-03-08';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(requestedHistoryRequests.at(-1)?.filter).toEqual({ date: '2026-03-08', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone });
    expect(requestedHistoryRequests.at(-1)?.page).toBe(0);
    expect(requestedHistoryRequests.at(-1)?.size).toBe(6);
    expect(previous.observed).toBe(true);
    const latest = new Subject<unknown>();
    historyPageResult = latest;
    input.value = '2026-03-09';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(previous.observed).toBe(false);
    latest.next([]);
    fixture.detectChanges();
    expect(text()).toContain('No orders in this period');
    historyPageResult = of([order(1, 'SUBMITTED')]);
    fixture.nativeElement.querySelector('.date-filters button').click();
    fixture.detectChanges();
    expect(requestedHistoryRequests.at(-1)?.filter).toBeUndefined();
    expect(text()).not.toContain('No orders in this period');
  });

  it('allows clearing after a filtered request fails', async () => {
    allOrdersResult = of([]);
    historyPageResult = of([]);
    await setup();
    historyPageResult = throwError(() => new HttpErrorResponse({ status: 500 }));
    const input: HTMLInputElement = fixture.nativeElement.querySelector('app-orders-panel input');
    input.value = '2026-03-08';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(text()).toContain('Unable to load orders');
    expect(text()).not.toContain('No orders in this period');
    historyPageResult = of([]);
    fixture.nativeElement.querySelector('.date-filters button').click();
    fixture.detectChanges();
    expect(text()).not.toContain('Unable to load orders');
  });

  it('requests the next backend page when pagination advances', async () => {
    allOrdersResult = of(Array.from({ length: 12 }, (_, i) => order(i + 1, 'FILLED')));
    historyPageResult = of(Array.from({ length: 6 }, (_, i) => order(12 - i, 'FILLED')));
    await setup();

    historyPageResult = of(Array.from({ length: 6 }, (_, i) => order(6 - i, 'FILLED')));
    const nextPageButton = fixture.nativeElement.querySelector('app-orders-panel .pg-btn[aria-label="Next page"]') as HTMLButtonElement;
    nextPageButton.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(requestedHistoryRequests.at(-1)?.page).toBe(1);
    expect(requestedHistoryRequests.at(-1)?.size).toBe(6);
  });

  it('requests backend page 0 size 6 on first history load', async () => {
    allOrdersResult = of(Array.from({ length: 4 }, (_, i) => order(i + 1, 'FILLED')));
    historyPageResult = of(Array.from({ length: 4 }, (_, i) => order(4 - i, 'FILLED')));

    await setup();

    expect(requestedHistoryRequests[0]).toEqual({
      page: 0,
      size: 6,
      filter: undefined,
    });
  });

  it('maps paged API response fields into rendered order-history columns', async () => {
    allOrdersResult = of([
      {
        success: true,
        orderId: 1,
        accountId: 7,
        instrumentId: 1,
        symbol: 'AAPL',
        orderType: 'BUY',
        quantity: 11,
        pricePerUnit: 201.5,
        orderStatus: 'FILLED',
        rejectionReason: null,
        submittedAt: '2026-03-08T10:00:00',
        createdAt: '2026-03-08T10:00:00',
        updatedAt: '2026-03-08T10:00:01',
        filledAt: '2026-03-08T10:00:01',
      },
      {
        success: true,
        orderId: 2,
        accountId: 7,
        instrumentId: 2,
        symbol: 'TSLA',
        orderType: 'SELL',
        quantity: 4,
        pricePerUnit: null,
        orderStatus: 'REJECTED',
        rejectionReason: 'INSUFFICIENT_HOLDINGS',
        submittedAt: '2026-03-08T11:00:00',
        createdAt: '2026-03-08T11:00:00',
        updatedAt: '2026-03-08T11:00:00',
        filledAt: null,
      },
    ]);
    historyPageResult = of([
      {
        success: true,
        orderId: 2,
        accountId: 7,
        instrumentId: 2,
        symbol: 'TSLA',
        orderType: 'SELL',
        quantity: 4,
        pricePerUnit: null,
        orderStatus: 'REJECTED',
        rejectionReason: 'INSUFFICIENT_HOLDINGS',
        submittedAt: '2026-03-08T11:00:00',
        createdAt: '2026-03-08T11:00:00',
        updatedAt: '2026-03-08T11:00:00',
        filledAt: null,
      },
      {
        success: true,
        orderId: 1,
        accountId: 7,
        instrumentId: 1,
        symbol: 'AAPL',
        orderType: 'BUY',
        quantity: 11,
        pricePerUnit: 201.5,
        orderStatus: 'FILLED',
        rejectionReason: null,
        submittedAt: '2026-03-08T10:00:00',
        createdAt: '2026-03-08T10:00:00',
        updatedAt: '2026-03-08T10:00:01',
        filledAt: '2026-03-08T10:00:01',
      },
    ]);

    await setup();

    const rows = fixture.nativeElement.querySelectorAll('app-orders-panel tbody tr[data-order-id]');
    expect(rows).toHaveLength(2);
    const firstCells = rowTextCells(rows[0]);
    const secondCells = rowTextCells(rows[1]);

    expect(firstCells[1]).toContain('TSLA');
    expect(firstCells[2]).toBe('SELL');
    expect(firstCells[3]).toBe('4');
    expect(firstCells[4]).toBe('Rejected');
    expect(firstCells[5]).toBe('—');
    expect(firstCells[6]).toContain('Order rejected because you do not hold enough shares to sell that quantity.');

    expect(secondCells[1]).toContain('AAPL');
    expect(secondCells[2]).toBe('BUY');
    expect(secondCells[3]).toBe('11');
    expect(secondCells[4]).toBe('Filled');
    expect(secondCells[5]).toContain('$201.50');
    expect(secondCells[6]).toBe('—');
  });
});
