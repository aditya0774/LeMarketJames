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

  it('requests every order when no filter is given', async () => {
    const result = service.getOrders();
    http.expectOne(ordersUrl).flush({ orders: [] });
    await result;
  });

  it('sends each filter that is set as a query parameter', async () => {
    const result = service.getOrders({ status: 'FILLED', limit: 10, offset: 20 });
    http.expectOne(`${ordersUrl}?status=FILLED&limit=10&offset=20`).flush({ orders: [] });
    await result;
  });

  it('leaves out filters that are not set', async () => {
    const result = service.getOrders({ status: '', limit: 5 });
    http.expectOne(`${ordersUrl}?limit=5`).flush({ orders: [] });
    await result;
  });
});
