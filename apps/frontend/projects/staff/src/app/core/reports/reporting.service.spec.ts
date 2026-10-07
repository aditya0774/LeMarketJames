import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReportingService, TradesReportResponse, TradesByStockRow } from './reporting.service';

describe('ReportingService', () => {
  let service: ReportingService;
  let httpMock: HttpTestingController;

  const mockResponse: TradesReportResponse = {
    success: true,
    data: [
      { symbol: 'AAPL', totalQuantity: 150, totalGrossAmount: 22500.75, buyCount: 8, sellCount: 5 },
      { symbol: 'MSFT', totalQuantity: 200, totalGrossAmount: 50200.00, buyCount: 10, sellCount: 3 },
    ]
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ReportingService]
    });

    service = TestBed.inject(ReportingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch trades by stock without date parameters', () => {
    service.getTradesByStock().subscribe(response => {
      expect(response).toEqual(mockResponse);
      expect(response.success).toBe(true);
      expect(response.data.length).toBe(2);
    });

    const req = httpMock.expectOne(request => 
      request.url === '/api/v1/reports/trades-by-stock' && 
      !request.params.has('startDate') && 
      !request.params.has('endDate')
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should fetch trades by stock with startDate only', () => {
    service.getTradesByStock('2026-10-01').subscribe(response => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(request => 
      request.url === '/api/v1/reports/trades-by-stock' && 
      request.params.get('startDate') === '2026-10-01' &&
      !request.params.has('endDate')
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should fetch trades by stock with endDate only', () => {
    service.getTradesByStock(undefined, '2026-10-07').subscribe(response => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(request => 
      request.url === '/api/v1/reports/trades-by-stock' && 
      !request.params.has('startDate') &&
      request.params.get('endDate') === '2026-10-07'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should fetch trades by stock with both date parameters', () => {
    service.getTradesByStock('2026-10-01', '2026-10-07').subscribe(response => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(request => 
      request.url === '/api/v1/reports/trades-by-stock' && 
      request.params.get('startDate') === '2026-10-01' &&
      request.params.get('endDate') === '2026-10-07'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should handle empty response', () => {
    const emptyResponse: TradesReportResponse = {
      success: true,
      data: []
    };

    service.getTradesByStock().subscribe(response => {
      expect(response.success).toBe(true);
      expect(response.data.length).toBe(0);
    });

    const req = httpMock.expectOne(request => 
      request.url === '/api/v1/reports/trades-by-stock'
    );
    req.flush(emptyResponse);
  });

  it('should handle error response', () => {
    service.getTradesByStock().subscribe(
      () => fail('should have failed'),
      error => {
        expect(error.status).toBe(403);
      }
    );

    const req = httpMock.expectOne(request => 
      request.url === '/api/v1/reports/trades-by-stock'
    );
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
  });
});
