import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { TradeReportService } from './trade-report.service';
import { TradeReportResponse } from '../models/trade-report.model';

describe('TradeReportService', () => {
  let service: TradeReportService;
  let httpMock: HttpTestingController;

  const response: TradeReportResponse = {
    data: [{ period: '2026-10', tradeCount: 3, buyCount: 2, sellCount: 1, totalValue: 1500 }],
    generatedAt: '2026-10-09T12:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TradeReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends only the period type when no range is given', () => {
    let result: TradeReportResponse | undefined;
    service.getTradeReport('MONTH').subscribe((r) => (result = r));

    const req = httpMock.expectOne((r) => r.url === '/api/v1/reports/trades');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('periodType')).toBe('MONTH');
    expect(req.request.params.has('from')).toBe(false);
    expect(req.request.params.has('to')).toBe(false);
    req.flush(response);

    expect(result).toEqual(response);
  });

  it('adds the from and to dates when given', () => {
    service.getTradeReport('DAY', '2026-10-01', '2026-10-09').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/reports/trades');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-10-09');
    req.flush(response);
  });

  it('errors when the server does not answer within 10 seconds', () => {
    vi.useFakeTimers();
    try {
      let error: unknown;
      service.getTradeReport('YEAR').subscribe({ error: (e) => (error = e) });
      httpMock.expectOne((r) => r.url === '/api/v1/reports/trades');

      vi.advanceTimersByTime(10_001);

      expect((error as Error).name).toBe('TimeoutError');
    } finally {
      vi.useRealTimers();
    }
  });
});
