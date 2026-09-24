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
});
