import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth } from '../auth/auth';
import { OrderRequest, OrderService } from './order.service';

describe('Order creation holdings validation', () => {
  let service: OrderService;
  let http: HttpTestingController;
  let accountId = signal<number | null>(1);
  const sell: OrderRequest = { accountId: 1, instrumentId: 42, orderType: 'SELL', quantity: 2.5 };

  beforeEach(() => {
    accountId = signal<number | null>(1);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: Auth, useValue: { currentAccountId: accountId } }
      ]
    });
    service = TestBed.inject(OrderService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('waits for successful holdings validation before submitting a SELL', () => {
    let result: unknown;
    service.createOrder(sell).subscribe(response => result = response);
    http.expectNone('/api/v1/orders');
    const validation = http.expectOne('/api/v1/holdings/validate');
    expect(validation.request.body).toEqual({ accountId: 1, instrumentId: 42, sellQuantity: 2.5 });
    validation.flush({ success: true });
    const order = http.expectOne('/api/v1/sell-orders');
    expect(order.request.method).toBe('POST');
    expect(order.request.body).toEqual({ accountId: 1, instrumentId: 42, quantity: 2.5 });
    order.flush({ orderId: 7 });
    expect(result).toEqual({ orderId: 7 });
  });

  it('submits BUY orders without checking holdings', () => {
    service.createOrder({ ...sell, orderType: 'BUY' }).subscribe();
    http.expectNone('/api/v1/holdings/validate');
    http.expectOne('/api/v1/orders').flush({ orderId: 8 });
  });

  for (const status of [400, 403, 500, 0]) {
    it(`does not submit a SELL when validation fails with status ${status}`, () => {
      let error: unknown;
      service.createOrder(sell).subscribe({ error: failure => error = failure });
      const validation = http.expectOne('/api/v1/holdings/validate');
      if (status === 0) {
        validation.error(new ProgressEvent('error'));
      } else {
        validation.flush({ error: 'Validation failed' }, { status, statusText: 'Error' });
      }
      expect(error).toBeTruthy();
      http.expectNone('/api/v1/sell-orders');
    });
  }

  it('rejects an unsuccessful validation response even with HTTP 200', () => {
    let error: Error | undefined;
    service.createOrder(sell).subscribe({ error: failure => error = failure });
    http.expectOne('/api/v1/holdings/validate').flush({ success: false, error: 'Insufficient holdings' });
    expect(error?.message).toBe('Insufficient holdings');
    http.expectNone('/api/v1/sell-orders');
  });

  it('rejects an account different from the one being validated', () => {
    let error: unknown;
    service.createOrder({ ...sell, accountId: 2 }).subscribe({ error: failure => error = failure });
    expect(error).toBeTruthy();
    http.expectNone('/api/v1/holdings/validate');
    http.expectNone('/api/v1/sell-orders');
  });

  it('submits the validated values if the form changes while validation is pending', () => {
    const request = { ...sell };
    service.createOrder(request).subscribe();
    request.quantity = 100;
    http.expectOne('/api/v1/holdings/validate').flush({ success: true });
    const order = http.expectOne('/api/v1/sell-orders');
    expect(order.request.body.quantity).toBe(2.5);
    order.flush({ orderId: 9 });
  });
  it('does not submit if the user logs out while validation is pending', () => {
    let error: unknown;
    service.createOrder(sell).subscribe({ error: failure => error = failure });
    const validation = http.expectOne('/api/v1/holdings/validate');
    accountId.set(null);
    validation.flush({ success: true });
    expect(error).toBeTruthy();
    http.expectNone('/api/v1/sell-orders');
  });

  it('submits sell order directly via dedicated sell-order endpoint', () => {
    service.submitSellOrder({ accountId: 1, instrumentId: 42, quantity: 2.5 }).subscribe();
    const order = http.expectOne('/api/v1/sell-orders');
    expect(order.request.method).toBe('POST');
    expect(order.request.body).toEqual({ accountId: 1, instrumentId: 42, quantity: 2.5 });
    order.flush({ orderId: 10 });
  });

});
