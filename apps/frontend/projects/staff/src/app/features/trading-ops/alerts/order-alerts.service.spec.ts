import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrderAlertsService } from './order-alerts.service';

describe('OrderAlertsService', () => {
  let service: OrderAlertsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OrderAlertsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the alerts with only the filters that are set', () => {
    service.list({ reason: 'LARGE_ORDER', from: '2026-10-01', to: undefined }).subscribe();

    const request = httpMock.expectOne((r) => r.url === '/api/v1/surveillance/alerts');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual(['reason', 'from']);
    request.flush({ success: true, largeOrderQuantity: 100, alerts: [] });
  });

  it('asks for every alert when nothing is set', () => {
    service.list().subscribe();

    expect(httpMock.expectOne('/api/v1/surveillance/alerts').request.params.keys()).toEqual([]);
  });
});
