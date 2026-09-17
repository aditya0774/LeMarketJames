import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HoldingsService } from './holdings.service';
import { HoldingsResponse, ValidateHoldingResponse } from '@app/shared/models/holdings.model';
import { Auth } from '../auth/auth';

describe('HoldingsService', () => {
  let service: HoldingsService;
  let httpMock: HttpTestingController;
  let authMock: any;

  beforeEach(() => {
    // Mock Auth service with currentAccountId signal
    authMock = {
      currentAccountId: signal<number | null>(1)
    };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        HoldingsService,
        { provide: Auth, useValue: authMock }
      ]
    });

    service = TestBed.inject(HoldingsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /**
   * AC1: Holdings retrieved - verify HTTP GET request
   * AC2: Requests scoped to authenticated user - accountId extracted from Auth service
   */
  it('should retrieve holdings for authenticated user', () => {
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
    service.getOwnHoldings().subscribe((response: HoldingsResponse) => {
      actualResponse = response;
    });

    const req = httpMock.expectOne('/api/v1/holdings?accountId=1');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(actualResponse).toBeTruthy();
    expect(actualResponse!.success).toBe(true);
    expect(actualResponse!.holdings.length).toBe(1);
    expect(actualResponse!.holdings[0].symbol).toBe('AAPL');
  });

  /**
   * AC1: Unauthorized data access prevented
   * Verify 403 ACCOUNT_ACCESS_DENIED error handled safely
   */
  it('should handle 403 ACCOUNT_ACCESS_DENIED error safely', () => {
    const mockErrorResponse = {
      code: 'ACCOUNT_ACCESS_DENIED',
      message: 'User is not authorized to access this account'
    };

    let errorOccurred = false;
    service.getOwnHoldings().subscribe({
      next: () => {
        throw new Error('Should have thrown error');
      },
      error: (error: any) => {
        errorOccurred = true;
      }
    });

    const req = httpMock.expectOne('/api/v1/holdings?accountId=1');
    req.flush(mockErrorResponse, { status: 403, statusText: 'Forbidden' });

    expect(errorOccurred).toBe(true);
    expect(service.error()).toBeTruthy();
    expect(service.error()?.code).toBe('ACCOUNT_ACCESS_DENIED');
    expect(service.error()?.message).toBe('You do not have permission to view this account.');
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
    
    service.validateOwnHoldings(1, 10)
      .subscribe({
        next: (response: ValidateHoldingResponse) => {
          actualErrorResponse = response;
        },
        error: (error: any) => {
          errorOccurred = true;
          actualErrorResponse = error.error;
        }
      });

    const req = httpMock.expectOne('/api/v1/holdings/validate');
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
    service.validateOwnHoldings(1, 5)
      .subscribe((response: ValidateHoldingResponse) => {
        actualResponse = response;
      });

    const req = httpMock.expectOne('/api/v1/holdings/validate');
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);

    expect(actualResponse).toBeTruthy();
    expect(actualResponse!.success).toBe(true);
  });

  /**
   * AC2: Requests scoped to authenticated user
   * Verify POST request includes accountId from Auth service
   */
  it('should include accountId in validate request', () => {
    const mockResponse: ValidateHoldingResponse = {
      success: true,
      message: 'Sufficient holdings available'
    };

    service.validateOwnHoldings(42, 5).subscribe();

    const req = httpMock.expectOne('/api/v1/holdings/validate');
    expect(req.request.body.accountId).toBe(1);
    expect(req.request.body.instrumentId).toBe(42);
    expect(req.request.body.sellQuantity).toBe(5);
    req.flush(mockResponse);
  });
  it('clears cached holdings when the authenticated account changes', () => {
    TestBed.tick();
    let latest: HoldingsResponse | null = null;
    service.holdings.subscribe(response => latest = response);
    service.getOwnHoldings().subscribe();
    httpMock.expectOne('/api/v1/holdings?accountId=1').flush({ success: true, holdings: [] });
    expect(latest).not.toBeNull();
    authMock.currentAccountId.set(2);
    TestBed.tick();
    expect(latest).toBeNull();
  });

  it('does not cache a late response belonging to a previous login', () => {
    TestBed.tick();
    let latest: HoldingsResponse | null = null;
    service.holdings.subscribe(response => latest = response);
    service.getOwnHoldings().subscribe();
    const request = httpMock.expectOne('/api/v1/holdings?accountId=1');
    authMock.currentAccountId.set(2);
    TestBed.tick();
    request.flush({ success: true, holdings: [{ symbol: 'PRIVATE', quantity: 10 }] });
    expect(latest).toBeNull();
  });

  it('does not send a holdings request without an authenticated account', () => {
    authMock.currentAccountId.set(null);
    expect(() => service.getOwnHoldings()).toThrow('No authenticated account found');
    expect(service.error()?.code).toBe('NO_ACCOUNT_ID');
    httpMock.expectNone(request => request.url.startsWith('/api/v1/holdings'));
  });

});
