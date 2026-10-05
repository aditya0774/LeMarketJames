import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TradeService } from './trade.service';
import { environment } from '../../../environments/environment';
import { TradeDto } from '../../shared/models/trade.model';

describe('TradeService', () => {
  let service: TradeService;
  let httpMock: HttpTestingController;

  const mockTrades: TradeDto[] = [
    {
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 10,
      pricePerUnit: 150.00,
      filledAt: '2026-09-21T10:30:00'
    },
    {
      symbol: 'GOOGL',
      side: 'SELL',
      quantity: 5,
      pricePerUnit: 140.50,
      filledAt: '2026-09-22T14:15:00'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TradeService]
    });

    service = TestBed.inject(TradeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Verify no outstanding HTTP requests
    httpMock.verify();
  });

  describe('getTrades', () => {
    /**
     * Test: Fetch trades for account
     * Verify correct endpoint is called with accountId
     */
    it('should fetch trades for an account', () => {
      const accountId = 1;

      service.getTrades(accountId).subscribe(trades => {
        expect(trades).toEqual(mockTrades);
        expect(trades.length).toBe(2);
      });

      const req = httpMock.expectOne(
        req => req.url === `${environment.apiBaseUrl}/api/v1/trades` &&
                req.params.get('accountId') === accountId.toString()
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockTrades);
    });

    /**
     * Test: Correct endpoint URL
     * Verify API endpoint matches contract
     */
    it('should use correct API endpoint', () => {
      const accountId = 1;

      service.getTrades(accountId).subscribe();

      const req = httpMock.expectOne(
        req => req.url === `${environment.apiBaseUrl}/api/v1/trades`
      );
      expect(req.request.url).toContain('/api/v1/trades');
    });

    /**
     * Test: Account ID is passed as parameter
     * Verify query parameter is included
     */
    it('should include accountId in query parameters', () => {
      const accountId = 42;

      service.getTrades(accountId).subscribe();

      const req = httpMock.expectOne(
        req => req.params.has('accountId')
      );
      expect(req.request.params.get('accountId')).toBe('42');
    });

    /**
     * Test: Empty response handling
     * Verify empty array is returned
     */
    it('should handle empty trade list', () => {
      const accountId = 1;

      service.getTrades(accountId).subscribe(trades => {
        expect(trades).toEqual([]);
        expect(trades.length).toBe(0);
      });

      const req = httpMock.expectOne(
        req => req.params.get('accountId') === accountId.toString()
      );
      req.flush([]);
    });

    /**
     * Test: Response format matches TradeDto
     * Verify all required fields are present
     */
    it('should return TradeDto objects with correct structure', () => {
      const accountId = 1;

      service.getTrades(accountId).subscribe(trades => {
        trades.forEach(trade => {
          expect(trade.symbol).toBeDefined();
          expect(trade.side).toBeDefined();
          expect(trade.quantity).toBeDefined();
          expect(trade.pricePerUnit).toBeDefined();
          expect(trade.filledAt).toBeDefined();
        });
      });

      const req = httpMock.expectOne(
        req => req.params.get('accountId') === accountId.toString()
      );
      req.flush(mockTrades);
    });
  });

  describe('getTradesBySymbol', () => {
    /**
     * Test: Fetch trades for specific symbol
     * Verify symbol parameter is included
     */
    it('should fetch trades for a specific symbol', () => {
      const accountId = 1;
      const symbol = 'AAPL';

      service.getTradesBySymbol(accountId, symbol).subscribe(trades => {
        expect(trades).toBeDefined();
      });

      const req = httpMock.expectOne(
        req => req.url === `${environment.apiBaseUrl}/api/v1/trades` &&
                req.params.get('accountId') === accountId.toString() &&
                req.params.get('symbol') === symbol
      );
      expect(req.request.method).toBe('GET');
      req.flush([mockTrades[0]]); // Return only AAPL trade
    });

    /**
     * Test: Symbol parameter is passed correctly
     * Verify query parameter format
     */
    it('should include symbol in query parameters', () => {
      const accountId = 1;
      const symbol = 'GOOGL';

      service.getTradesBySymbol(accountId, symbol).subscribe();

      const req = httpMock.expectOne(
        req => req.params.has('symbol')
      );
      expect(req.request.params.get('symbol')).toBe('GOOGL');
    });

    /**
     * Test: Both accountId and symbol parameters
     * Verify both params are included
     */
    it('should include both accountId and symbol parameters', () => {
      const accountId = 5;
      const symbol = 'TSLA';

      service.getTradesBySymbol(accountId, symbol).subscribe();

      const req = httpMock.expectOne(
        req => req.params.get('accountId') === '5' &&
                req.params.get('symbol') === 'TSLA'
      );
      expect(req.request.params.get('accountId')).toBe('5');
      expect(req.request.params.get('symbol')).toBe('TSLA');
    });

    /**
     * Test: Empty response for non-existent symbol
     * Verify empty array returned
     */
    it('should return empty array for symbol with no trades', () => {
      const accountId = 1;
      const symbol = 'NONEXISTENT';

      service.getTradesBySymbol(accountId, symbol).subscribe(trades => {
        expect(trades).toEqual([]);
      });

      const req = httpMock.expectOne(
        req => req.params.get('symbol') === 'NONEXISTENT'
      );
      req.flush([]);
    });
  });

  describe('Error Handling', () => {
    /**
     * Test: 403 Forbidden (unauthorized account)
     * Verify error is propagated
     */
    it('should handle 403 Forbidden error', () => {
      const accountId = 1;

      service.getTrades(accountId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error.status).toBe(403);
        }
      );

      const req = httpMock.expectOne(
        req => req.params.get('accountId') === accountId.toString()
      );
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
    });

    /**
     * Test: 500 Server error
     * Verify error is propagated
     */
    it('should handle 500 Server error', () => {
      const accountId = 1;

      service.getTrades(accountId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error.status).toBe(500);
        }
      );

      const req = httpMock.expectOne(
        req => req.params.get('accountId') === accountId.toString()
      );
      req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });
    });

    /**
     * Test: 400 Bad Request
     * Verify error is propagated
     */
    it('should handle 400 Bad Request error', () => {
      const accountId = -1; // Invalid account ID

      service.getTrades(accountId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error.status).toBe(400);
        }
      );

      const req = httpMock.expectOne(
        req => req.params.get('accountId') === accountId.toString()
      );
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });
    });
  });
});
