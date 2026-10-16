import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrdersPanel } from './orders-panel';
import { OrderResponse, OrderService } from '../../../core/orders/order.service';
import { HoldingsService } from '../../../core/holdings/holdings.service';
import { Auth } from '../../../core/auth/auth';
import { environment } from '../../../../environments/environment';

function order(orderId: number, submittedAt: string): OrderResponse {
  return {
    success: true, orderId, accountId: 7, instrumentId: orderId,
    orderType: 'BUY', quantity: 1, pricePerUnit: 10, orderStatus: 'FILLED',
    submittedAt, createdAt: submittedAt, updatedAt: submittedAt,
    filledAt: '2026-12-31T12:00:00',
  };
}

function rejectedOrder(reason: string): OrderResponse {
  return {
    success: true,
    orderId: 999,
    accountId: 7,
    instrumentId: 1,
    orderType: 'BUY',
    quantity: 1,
    pricePerUnit: 10,
    orderStatus: 'REJECTED',
    rejectionReason: reason,
    submittedAt: '2026-03-08T12:00:00',
    createdAt: '2026-03-08T12:00:00',
    updatedAt: '2026-03-08T12:00:00',
  };
}

function backendSymbolOrder(symbol: string): OrderResponse {
  return {
    success: true,
    orderId: 1001,
    accountId: 7,
    instrumentId: 1,
    symbol,
    orderType: 'BUY',
    quantity: 3,
    pricePerUnit: 99.5,
    orderStatus: 'FILLED',
    submittedAt: '2026-10-12T12:00:00',
    createdAt: '2026-10-12T12:00:00',
    updatedAt: '2026-10-12T12:00:00',
    filledAt: '2026-10-12T12:00:02',
  };
}

function pendingOrder(orderId: number, submittedAt: string): OrderResponse {
  return {
    success: true,
    orderId,
    accountId: 7,
    instrumentId: 1,
    orderType: 'SELL',
    quantity: 4,
    pricePerUnit: null,
    orderStatus: 'PENDING',
    submittedAt,
    createdAt: submittedAt,
    updatedAt: submittedAt,
    acceptedAt: null,
    filledAt: null,
  };
}

describe('OrdersPanel date filtering', () => {
  let fixture: ComponentFixture<OrdersPanel>;
  const seeds = [
    order(901, '2024-02-29T12:00:00'), order(902, '2025-01-01T00:00:00'),
    order(903, '2026-01-01T00:00:00'), order(904, '2026-02-01T12:00:00'),
    order(905, '2026-02-28T23:59:59'), order(906, '2026-03-01T00:00:00'),
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersPanel],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: HoldingsService, useValue: {} }, { provide: Auth, useValue: {} }],
    }).compileComponents();
    fixture = TestBed.createComponent(OrdersPanel);
    // Exercise the real account-history service against a mocked HTTP contract.
    TestBed.inject(OrderService).getOrdersByAccountId(7).subscribe(orders =>
      fixture.componentRef.setInput('orders', orders));
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiBaseUrl}/api/v1/orders/account/7`).flush(seeds);
    http.verify();
    fixture.detectChanges();
  });

  function choose(period: string, value: string): void {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = period;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function symbols(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody .sym'),
      (element: any) => element.textContent.trim());
  }

  function orderIdsInView(): number[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('tbody tr[data-order-id]'),
      (element: HTMLElement) => Number(element.dataset['orderId']),
    );
  }

  function rowCellText(row: number, col: number): string {
    const rows = fixture.nativeElement.querySelectorAll('tbody tr[data-order-id]');
    const cells = rows[row].querySelectorAll('td');
    return (cells[col]?.textContent ?? '').trim();
  }

  function cellTextForOrder(orderId: number, col: number): string {
    const row = fixture.nativeElement.querySelector(`tbody tr[data-order-id="${orderId}"]`) as HTMLElement | null;
    const cell = row?.querySelectorAll('td').item(col);
    return (cell?.textContent ?? '').trim();
  }

  it('emits day, month and year selections for the container to fetch', () => {
    const changes: string[] = [];
    fixture.componentInstance.dateChange.subscribe(value => changes.push(value));
    choose('day', '2024-02-29');
    choose('month', '2026-02');
    choose('year', '2026');
    expect(changes).toEqual(['', '2024-02-29', '', '2026-02', '', '2026']);
  });

  it('does not locally re-filter the server response', () => {
    choose('year', '2023');
    expect(symbols().length).toBe(5);
  });

  it('renders newest-first rows by submitted timestamp', () => {
    fixture.componentRef.setInput('orders', [
      order(101, '2026-01-01T00:00:00'),
      order(102, '2026-03-01T00:00:00'),
      order(103, '2026-02-01T00:00:00'),
    ]);
    fixture.detectChanges();
    expect(orderIdsInView()).toEqual([102, 103, 101]);
  });

  it('does not request incomplete years', () => {
    const changes: string[] = [];
    fixture.componentInstance.dateChange.subscribe(value => changes.push(value));
    choose('year', '20');
    expect(changes).toEqual(['']);
  });

  it('shows the exact no-results message for an empty API result', () => {
    choose('year', '2023');
    fixture.componentRef.setInput('orders', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty-row').textContent.trim())
      .toBe('No orders in this period');
    expect(fixture.nativeElement.querySelector('.panel-foot')).toBeNull();
  });

  it('clears date and status restrictions and resets pagination', () => {
    const changes: string[] = [];
    fixture.componentInstance.dateChange.subscribe(value => changes.push(value));
    fixture.componentInstance.goToPage(2);
    choose('year', '2026');
    fixture.componentInstance.setFilter('REJECTED');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.date-filters button').click();
    fixture.detectChanges();
    expect(changes.at(-1)).toBe('');
    expect(symbols().length).toBe(5);
    expect(fixture.nativeElement.querySelector('.pg-info').textContent).toContain('of 6 orders');
    expect(fixture.nativeElement.querySelector('input').value).toBe('');
  });

  it('navigates between pages for six orders', () => {
    expect(orderIdsInView()).toEqual([906, 905, 904, 903, 902]);

    fixture.componentInstance.goToPage(2);
    fixture.detectChanges();

    expect(orderIdsInView()).toEqual([901]);
    expect(fixture.nativeElement.querySelector('.pg-info').textContent).toContain('Showing 6–6 of 6 orders');

    fixture.componentInstance.goToPage(1);
    fixture.detectChanges();
    expect(orderIdsInView()).toEqual([906, 905, 904, 903, 902]);
  });

  it('keeps loading and failures distinct from empty results', () => {
    choose('year', '2023');
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading orders');
    expect(fixture.nativeElement.textContent).not.toContain('No orders in this period');
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('error', 'Unable to load orders');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Unable to load orders');
  });

  it('shows reconnecting indicator while the live stream is retrying', () => {
    fixture.componentRef.setInput('liveConnection', 'reconnecting');
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.live-badge') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('Reconnecting');
    expect(badge.classList.contains('reconnecting')).toBe(true);
  });

  it('renders plain-language rejection text from a reason code', () => {
    fixture.componentRef.setInput('orders', [rejectedOrder('INSUFFICIENT_CASH')]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent)
      .toContain('Order rejected because your available cash is too low for this buy order.');
  });

  it('renders fill price for filled rows and dash for non-filled rows', () => {
    fixture.componentRef.setInput('orders', [
      order(2001, '2026-03-08T12:00:00'),
      pendingOrder(2002, '2026-03-08T12:01:00'),
    ]);
    fixture.detectChanges();

    expect(cellTextForOrder(2001, 5)).toContain('$');
    expect(cellTextForOrder(2002, 5)).toBe('—');
  });

  // The Status cell (column 5) carries the badge and, under it, the reason or what happens next.
  it('shows the rejection reason under a rejected status and a next-step note otherwise', () => {
    fixture.componentRef.setInput('orders', [
      rejectedOrder('INSUFFICIENT_CASH'),
      pendingOrder(3002, '2026-03-08T12:01:00'),
    ]);
    fixture.detectChanges();

    expect(cellTextForOrder(999, 4)).toContain('Order rejected because your available cash is too low for this buy order.');
    expect(cellTextForOrder(3002, 4)).not.toContain('Order rejected');
    expect(fixture.nativeElement.querySelector('thead tr').children).toHaveLength(6);
  });

  it('says what a filled order cost', () => {
    fixture.componentRef.setInput('orders', [order(2001, '2026-03-08T12:00:00')]);
    fixture.detectChanges();

    expect(cellTextForOrder(2001, 4)).toMatch(/Done\. You (paid|received) \$/);
  });

  it('lights the progress bar up to the status reached', () => {
    fixture.componentRef.setInput('orders', [pendingOrder(3002, '2026-03-08T12:01:00')]);
    fixture.detectChanges();

    const lit = fixture.nativeElement.querySelectorAll('tr[data-order-id="3002"] .steps i.on');
    expect(lit).toHaveLength(3);
  });

  it('renders backend symbol when provided in the API payload', () => {
    fixture.componentRef.setInput('orders', [backendSymbolOrder('AAPL')]);
    fixture.detectChanges();
    const symbol = fixture.nativeElement.querySelector('.sym')?.textContent?.trim();
    expect(symbol).toBe('AAPL');
  });

  it('uses server pagination controls by emitting pageChange instead of local page toggles', () => {
    const pageChanges: number[] = [];
    fixture.componentRef.setInput('orders', seeds.slice(0, 5));
    fixture.componentRef.setInput('serverPagination', true);
    fixture.componentRef.setInput('currentPage', 1);
    fixture.componentRef.setInput('hasNextPage', true);
    fixture.componentRef.setInput('totalOrders', 15);
    fixture.componentInstance.pageChange.subscribe(page => pageChanges.push(page));
    fixture.detectChanges();

    const next = fixture.nativeElement.querySelector('.pg-btn[aria-label="Next page"]') as HTMLButtonElement;
    const previous = fixture.nativeElement.querySelector('.pg-btn[aria-label="Previous page"]') as HTMLButtonElement;
    next.click();
    fixture.detectChanges();
    previous.click();
    fixture.detectChanges();

    expect(pageChanges).toEqual([2]);
    expect(previous.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.pg-info').textContent).toContain('Showing 1–5 of 15 orders');
    expect(fixture.nativeElement.querySelectorAll('.pg-row .pg-btn').length).toBe(2);
  });

  // One page of rows is not the total, so an unknown server total is left out rather than shown wrong.
  it('leaves the total out of the footer while a server-paged total is unknown', () => {
    fixture.componentRef.setInput('orders', seeds.slice(0, 5));
    fixture.componentRef.setInput('serverPagination', true);
    fixture.componentRef.setInput('currentPage', 1);
    fixture.componentRef.setInput('hasNextPage', true);
    fixture.componentRef.setInput('totalOrders', null);
    fixture.detectChanges();

    const info = fixture.nativeElement.querySelector('.pg-info').textContent;
    expect(info).toContain('Showing 1–5 orders');
    expect(info).not.toContain(' of ');
  });

  it('ignores a period other than day, month or year', () => {
    const changes: string[] = [];
    fixture.componentInstance.dateChange.subscribe(value => changes.push(value));
    fixture.componentInstance.setPeriod('week');
    expect(changes).toEqual([]);
  });

  it('requests no page before the first in server pagination', () => {
    const pageChanges: number[] = [];
    fixture.componentRef.setInput('serverPagination', true);
    fixture.componentInstance.pageChange.subscribe(page => pageChanges.push(page));
    fixture.componentInstance.goToPage(0);
    expect(pageChanges).toEqual([]);
  });

  it('disables next in server pagination when hasNextPage is false', () => {
    fixture.componentRef.setInput('serverPagination', true);
    fixture.componentRef.setInput('currentPage', 2);
    fixture.componentRef.setInput('hasNextPage', false);
    fixture.componentRef.setInput('totalOrders', 9);
    fixture.detectChanges();

    const next = fixture.nativeElement.querySelector('.pg-btn[aria-label="Next page"]') as HTMLButtonElement;
    const previous = fixture.nativeElement.querySelector('.pg-btn[aria-label="Previous page"]') as HTMLButtonElement;
    expect(previous.disabled).toBe(false);
    expect(next.disabled).toBe(true);
  });
});
