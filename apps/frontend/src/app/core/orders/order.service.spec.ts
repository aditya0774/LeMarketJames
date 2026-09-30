import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { Auth } from '../auth/auth';
import { HoldingsService } from '../holdings/holdings.service';
import { OrderService } from './order.service';

describe('BUY order HTTP integration', () => {
  let http: HttpTestingController;
  let service: OrderService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(), OrderService,
      { provide: Auth, useValue: {} },
      { provide: HoldingsService, useValue: {} },
    ] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(OrderService);
  });

  afterEach(() => http.verify());

  it('posts a typed BUY without a client price and returns the created order', () => {
    const request = { accountId: 7, instrumentId: 5, orderType: 'BUY' as const, quantity: 2 };
    let receivedId: number | undefined;
    service.createOrder(request).subscribe(order => receivedId = order.orderId);
    const call = http.expectOne(`${environment.apiBaseUrl}/api/v1/orders`);
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({ success: true, orderId: 42, orderStatus: 'SUBMITTED' }, { status: 201, statusText: 'Created' });
    expect(receivedId).toBe(42);
  });

  it('preserves backend error details for the form', () => {
    let code: string | undefined;
    service.createOrder({ accountId: 7, instrumentId: 5, orderType: 'BUY', quantity: 2 })
      .subscribe({ error: error => code = error.error.code });
    http.expectOne(`${environment.apiBaseUrl}/api/v1/orders`)
      .flush({ success: false, code: 'INSUFFICIENT_CASH' }, { status: 400, statusText: 'Bad Request' });
    expect(code).toBe('INSUFFICIENT_CASH');
  });
  it('sends a local period and zone and normalizes UTC placement timestamps', () => {
    let submittedAt = '';
    service.getOrdersByAccountId(7, { date: '2026-03', timeZone: 'America/New_York' })
      .subscribe(orders => submittedAt = orders[0].submittedAt);
    const call = http.expectOne(request => request.url.endsWith('/account/7'));
    expect(call.request.params.get('date')).toBe('2026-03');
    expect(call.request.params.get('timeZone')).toBe('America/New_York');
    call.flush([{ submittedAt: '2026-03-08T05:00:00' }]);
    expect(submittedAt).toBe('2026-03-08T05:00:00Z');
  });

  it('omits date parameters when cleared and preserves explicit offsets', () => {
    let submittedAt = '';
    service.getOrdersByAccountId(7).subscribe(orders => submittedAt = orders[0].submittedAt);
    const call = http.expectOne(`${environment.apiBaseUrl}/api/v1/orders/account/7`);
    expect(call.request.params.keys()).toEqual([]);
    call.flush([{ submittedAt: '2026-01-01T00:00:00+05:30' }]);
    expect(submittedAt).toBe('2026-01-01T00:00:00+05:30');
  });
});
