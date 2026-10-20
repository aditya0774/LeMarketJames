import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { OrderResponse, OrdersStreamSnapshot, OrderService } from '../../core/orders/order.service';
import { loadTestInstruments } from '../../core/market/instrument-catalog.testing';
import { OrdersPage } from './orders-page';

function order(orderId: number, orderStatus: OrderResponse['orderStatus'], instrumentId = 1): OrderResponse {
  const day = String(((orderId - 1) % 28) + 1).padStart(2, '0');
  return {
    success: true,
    orderId, accountId: 7, instrumentId, orderType: 'BUY', quantity: 1, orderStatus,
    submittedAt: `2026-09-${day}T10:00:00`, createdAt: '', updatedAt: '',
  };
}

function rowTextCells(row: Element): string[] {
  return Array.from(row.querySelectorAll('td')).map((cell) => (cell.textContent ?? '').trim());
}

describe('OrdersPage', () => {
  let fixture: ComponentFixture<OrdersPage>;
  let allOrdersResult: Observable<unknown>;
  let historyPageResult: Observable<unknown>;
  let requests: Array<{ page: number; size: number; filter: unknown }>;
  let liveOrders: Subject<OrdersStreamSnapshot>;

  async function setup(accountId: number | null = 7) {
    requests = [];
    liveOrders = new Subject<OrdersStreamSnapshot>();
    await TestBed.configureTestingModule({
      imports: [OrdersPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: { currentUser: signal('lebron'), currentAccountId: signal(accountId) } },
        {
          provide: OrderService,
          useValue: {
            getAllOrdersByAccountId: () => allOrdersResult,
            getOrdersByAccountIdPage: (_accountId: number, request: { page: number; size: number }, filter: unknown) => {
              requests.push({ page: request.page, size: request.size, filter });
              return historyPageResult;
            },
            watchOrderStatusStream: () => liveOrders.asObservable(),
          },
        },
      ],
    }).compileComponents();
    await loadTestInstruments();

    fixture = TestBed.createComponent(OrdersPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const render = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };
  const filled = { orderId: 1, accountId: 7, from: 'SUBMITTED', to: 'FILLED', occurredAt: '2026-09-01T10:00:00Z' } as const;

  it('requests backend page 0 size 6 on first load', async () => {
    allOrdersResult = of([order(1, 'FILLED')]);
    historyPageResult = of([order(1, 'FILLED')]);
    await setup();

    expect(requests[0]).toEqual({ page: 0, size: 6, filter: undefined });
    expect(text()).toContain('Showing 1–1 of 1 orders');
  });

  it('refreshes the visible page after an order fills via live updates', async () => {
    allOrdersResult = of([order(1, 'SUBMITTED')]);
    historyPageResult = of([order(1, 'SUBMITTED')]);
    await setup();

    allOrdersResult = of([order(1, 'FILLED')]);
    historyPageResult = of([order(1, 'FILLED')]);
    const before = requests.length;
    liveOrders.next({ connection: 'live', event: filled });
    await render();

    expect(requests.length).toBeGreaterThan(before);
    expect(text()).toContain('Filled');
    expect(fixture.nativeElement.querySelector('.live-badge').textContent.trim()).toBe('Live');
  });

  it('requests no orders, history or live updates for a login without a trading account', async () => {
    allOrdersResult = of([]);
    historyPageResult = of([]);
    await setup(null);

    expect(text()).toContain('No trading account is linked to this login.');
    expect(requests).toEqual([]);
    expect(liveOrders.observed).toBe(false);
  });

  it('queues an orders refresh when a live event arrives during an in-flight orders request', async () => {
    const first = new Subject<OrderResponse[]>();
    allOrdersResult = first;
    historyPageResult = of([order(1, 'SUBMITTED')]);
    await setup();

    allOrdersResult = of([order(1, 'FILLED')]);
    historyPageResult = of([order(1, 'FILLED')]);
    liveOrders.next({ connection: 'live', event: filled });
    first.next([order(1, 'SUBMITTED')]);
    first.complete();
    await render();

    expect(text()).toContain('Filled');
  });

  // The first page can come back before the full order list; the total must never read 0 meanwhile.
  it('does not show a total of 0 while the full order list is still loading', async () => {
    const allOrders = new Subject<OrderResponse[]>();
    const seven = [1, 2, 3, 4, 5, 6, 7].map((id) => order(id, 'FILLED'));
    allOrdersResult = allOrders;
    historyPageResult = of(seven.slice(0, 6));
    await setup();

    expect(text()).toContain('Showing 1–5 orders');
    expect(text()).not.toContain('of 0 orders');

    allOrders.next(seven);
    allOrders.complete();
    await render();

    expect(text()).toContain('Showing 1–5 of 7 orders');
  });

  it('filters orders by status chip', async () => {
    allOrdersResult = of([order(1, 'FILLED'), order(2, 'REJECTED'), order(3, 'PENDING')]);
    historyPageResult = of([order(1, 'FILLED'), order(2, 'REJECTED'), order(3, 'PENDING')]);
    await setup();

    const chips = fixture.nativeElement.querySelectorAll('.chip') as NodeListOf<HTMLButtonElement>;
    chips[3].click(); // Rejected
    await render();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
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
    const input: HTMLInputElement = fixture.nativeElement.querySelector('.date-filters input');
    const previous = new Subject<unknown>();
    historyPageResult = previous;
    input.value = '2026-03-08';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(requests.at(-1)).toEqual({
      page: 0,
      size: 6,
      filter: { date: '2026-03-08', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone },
    });
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
    expect(requests.at(-1)?.filter).toBeUndefined();
    expect(text()).not.toContain('No orders in this period');
  });

  it('allows clearing after a filtered request fails', async () => {
    allOrdersResult = of([]);
    historyPageResult = of([]);
    await setup();
    historyPageResult = throwError(() => new HttpErrorResponse({ status: 500 }));
    const input: HTMLInputElement = fixture.nativeElement.querySelector('.date-filters input');
    input.value = '2026-03-08';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(text()).toContain('Unable to load orders');

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
    (fixture.nativeElement.querySelector('.pg-btn[aria-label="Next page"]') as HTMLButtonElement).click();
    await render();

    expect(requests.at(-1)).toEqual({ page: 1, size: 6, filter: undefined });
  });

  it('maps paged API response fields into the rendered columns', async () => {
    const rejected: OrderResponse = {
      success: true, orderId: 2, accountId: 7, instrumentId: 2, symbol: 'TSLA', orderType: 'SELL', quantity: 4,
      pricePerUnit: null, orderStatus: 'REJECTED', rejectionReason: 'INSUFFICIENT_HOLDINGS',
      submittedAt: '2026-03-08T11:00:00', createdAt: '2026-03-08T11:00:00', updatedAt: '2026-03-08T11:00:00', filledAt: null,
    };
    const filledOrder: OrderResponse = {
      success: true, orderId: 1, accountId: 7, instrumentId: 1, symbol: 'AAPL', orderType: 'BUY', quantity: 11,
      pricePerUnit: 201.5, orderStatus: 'FILLED', rejectionReason: null,
      submittedAt: '2026-03-08T10:00:00', createdAt: '2026-03-08T10:00:00', updatedAt: '2026-03-08T10:00:01', filledAt: '2026-03-08T10:00:01',
    };
    allOrdersResult = of([filledOrder, rejected]);
    historyPageResult = of([rejected, filledOrder]);
    await setup();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr[data-order-id]');
    expect(rows).toHaveLength(2);
    const [first, second] = [rowTextCells(rows[0]), rowTextCells(rows[1])];

    expect(first[1]).toContain('TSLA');
    expect(first[2]).toBe('SELL');
    expect(first[3]).toBe('4');
    expect(first[4]).toContain('Rejected');
    expect(first[4]).toContain('Order rejected because you do not hold enough shares to sell that quantity.');
    expect(first[5]).toBe('—');

    expect(second[1]).toContain('AAPL');
    expect(second[2]).toBe('BUY');
    expect(second[3]).toBe('11');
    expect(second[4]).toContain('Filled');
    expect(second[4]).toContain('Done. You paid $2,216.50.');
    expect(second[5]).toContain('$201.50');
  });
});
