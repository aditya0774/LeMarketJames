import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { BalanceService } from './orders.service';

describe('BalanceService order list', () => {
  const ordersUrl = `${environment.apiBaseUrl}/api/orders`;
  let service: BalanceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BalanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // Requests the orders with the given filters and returns the URL that was called.
  async function requestedUrl(filters?: Parameters<BalanceService['getOrders']>[0]): Promise<string> {
    const result = service.getOrders(filters);
    const request = http.expectOne(() => true);
    request.flush({ orders: [] });
    expect(await result).toEqual({ orders: [] });
    return request.request.url;
  }

  it('requests every order when no filter is given', async () => {
    expect(await requestedUrl()).toBe(ordersUrl);
  });

  it('sends each filter that is set as a query parameter', async () => {
    expect(await requestedUrl({ status: 'FILLED', limit: 10, offset: 20 }))
      .toBe(`${ordersUrl}?status=FILLED&limit=10&offset=20`);
  });

  it('leaves out filters that are not set', async () => {
    expect(await requestedUrl({ status: '', limit: 5 })).toBe(`${ordersUrl}?limit=5`);
  });
});
