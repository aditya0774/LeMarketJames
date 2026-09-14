import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HoldingsService } from './holdings.service';
import { HoldingsResponse, ValidateHoldingResponse } from '@app/shared/models/holdings.model';

describe('HoldingsService', () => {
  let service: HoldingsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [HoldingsService]
    });

    service = TestBed.inject(HoldingsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /**
   * AC1: Holdings retrieved - verify HTTP GET request
   */
  it('should retrieve holdings for account', () => {
    const mockResponse: HoldingsResponse = {
      success: true,
      holdings: [
        {
          symbol: 'AAPL',
          quantity: 10,
          averageCost: 150.00,
          currentPrice: 155.00,
          totalCost: 1500.00,
          currentValue: 1550.00,
          gainLoss: 50.00,
          gainLossPercent: 3.33
        }
      ]
    };

    let actualResponse: HoldingsResponse | undefined;
    service.getHoldings(1).subscribe((response) => {
      actualResponse = response;
    });

    const req = httpMock.expectOne('/api/holdings?accountId=1');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(actualResponse).toBeTruthy();
    expect(actualResponse!.success).toBe(true);
    expect(actualResponse!.holdings.length).toBe(1);
    expect(actualResponse!.holdings[0].symbol).toBe('AAPL');
  });

  /**
   * AC2: Overselling rejected - verify validation endpoint rejects overselling
   */
  it('should return error on insufficient holdings', () => {
    const mockResponse: ValidateHoldingResponse = {
      success: false,
      error: 'Insufficient holdings. Available: 5.0000, Requested: 10.0000',
      code: 'INSUFFICIENT_HOLDINGS'
    };

    let errorOccurred = false;
    let actualErrorResponse: any;
    
    service.validateHoldings({ accountId: 1, instrumentId: 1, sellQuantity: 10 })
      .subscribe({
        next: (response) => {
          actualErrorResponse = response;
        },
        error: (error) => {
          errorOccurred = true;
          actualErrorResponse = error.error;
        }
      });

    const req = httpMock.expectOne('/api/holdings/validate');
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse, { status: 400, statusText: 'Bad Request' });

    expect(errorOccurred).toBe(true);
    expect(actualErrorResponse).toBeTruthy();
    expect(actualErrorResponse.success).toBe(false);
    expect(actualErrorResponse.error).toBeDefined();
  });

  /**
   * AC2: Overselling rejected - verify validation endpoint allows sufficient holdings
   */
  it('should allow sufficient holdings', () => {
    const mockResponse: ValidateHoldingResponse = {
      success: true,
      message: 'Sufficient holdings available'
    };

    let actualResponse: ValidateHoldingResponse | undefined;
    service.validateHoldings({ accountId: 1, instrumentId: 1, sellQuantity: 5 })
      .subscribe((response) => {
        actualResponse = response;
      });

    const req = httpMock.expectOne('/api/holdings/validate');
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);

    expect(actualResponse).toBeTruthy();
    expect(actualResponse!.success).toBe(true);
  });
});
