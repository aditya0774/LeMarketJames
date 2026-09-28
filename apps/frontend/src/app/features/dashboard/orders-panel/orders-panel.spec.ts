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

  it('filters a day by placement rather than fill time', () => {
    choose('day', '2024-02-29');
    expect(symbols()).toEqual(['#901']);
  });

  it('includes both ends of a month and excludes neighbouring months', () => {
    choose('month', '2026-02');
    expect(symbols()).toEqual(['#905', '#904']);
  });

  it('filters the selected year', () => {
    choose('year', '2026');
    expect(symbols()).toEqual(['#906', '#905', '#904', '#903']);
  });

  it('shows the exact no-results message', () => {
    choose('year', '2023');
    expect(fixture.nativeElement.querySelector('.empty-row').textContent.trim())
      .toBe('No orders in this period');
    expect(fixture.nativeElement.querySelector('.panel-foot')).toBeNull();
  });

  it('clears both date and status restrictions and resets pagination', () => {
    fixture.componentInstance.goToPage(2);
    choose('year', '2026');
    expect(symbols().length).toBe(4);
    fixture.componentInstance.setFilter('REJECTED');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.date-filters button').click();
    fixture.detectChanges();
    expect(symbols().length).toBe(5);
    expect(fixture.nativeElement.querySelector('.pg-info').textContent).toContain('of 6 orders');
    expect(fixture.nativeElement.querySelector('input').value).toBe('');
  });

  it('uses local calendar boundaries for UTC timestamps', () => {
    // On a non-UTC test run these instants differ from their UTC calendar dates.
    fixture.componentRef.setInput('orders', [
      order(910, new Date(2026, 0, 1, 0, 0, 0).toISOString()),
      order(911, new Date(2025, 11, 31, 23, 59, 59).toISOString()),
      order(912, new Date(2026, 0, 2, 0, 0, 0).toISOString()),
    ]);
    choose('day', '2026-01-01');
    expect(symbols()).toEqual(['#910']);
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
});
