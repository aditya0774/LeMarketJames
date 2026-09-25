import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Quotes } from './quotes';

describe('Quotes', () => {
  let quotes: Quotes;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/api/quotes`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    quotes = TestBed.inject(Quotes);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch a quote by symbol from the contract endpoint', async () => {
    const fetchPromise = quotes.getQuote('AAPL');

    const request = httpMock.expectOne(`${baseUrl}/AAPL`);
    expect(request.request.method).toBe('GET');
    request.flush({
      success: true,
      quote: {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        price: 150.25,
        priceChange: 2.45,
        priceChangePercent: 1.66,
        highPrice: 151.5,
        lowPrice: 148.75,
        openPrice: 148,
        volume: 52345600,
        marketCap: 2350000000000,
        peRatio: 28.5,
        dividendYield: 0.42,
        lastUpdate: '2024-01-15T16:00:00Z',
      },
    });

    const response = await fetchPromise;
    expect(response.success).toBe(true);
    expect(response.quote.symbol).toBe('AAPL');
  });

  describe('watchQuote', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should fetch immediately and then on every refresh interval', () => {
      const prices: number[] = [];
      const subscription = quotes.watchQuote('AAPL', 1000).subscribe((response) => {
        prices.push(response.quote.price);
      });

      vi.advanceTimersByTime(0);
      httpMock.expectOne(`${baseUrl}/AAPL`).flush({ success: true, quote: { symbol: 'AAPL', price: 227.55 } });

      vi.advanceTimersByTime(1000);
      httpMock.expectOne(`${baseUrl}/AAPL`).flush({ success: true, quote: { symbol: 'AAPL', price: 227.61 } });

      expect(prices).toEqual([227.55, 227.61]);

      subscription.unsubscribe();
      vi.advanceTimersByTime(5000);
      httpMock.expectNone(`${baseUrl}/AAPL`);
    });

    it('watchQuotes should fetch every symbol in one batch request per refresh', () => {
      const emissions: Record<string, unknown>[] = [];
      const subscription = quotes.watchQuotes(['AAPL', 'NOPE'], 1000).subscribe((m) => emissions.push(m));

      vi.advanceTimersByTime(0);
      const request = httpMock.expectOne(baseUrl);
      expect(request.request.method).toBe('GET');
      // Symbols missing from the batch (unknown to the market) map to null without blanking the rest.
      request.flush({ success: true, quotes: [{ symbol: 'AAPL', price: 227.55 }, { symbol: 'MSFT', price: 510 }] });

      expect(emissions).toEqual([{ AAPL: { symbol: 'AAPL', price: 227.55 }, NOPE: null }]);

      vi.advanceTimersByTime(1000);
      httpMock.expectOne(baseUrl).flush({ success: true, quotes: [{ symbol: 'AAPL', price: 227.61 }] });
      expect(emissions[1]).toEqual({ AAPL: { symbol: 'AAPL', price: 227.61 }, NOPE: null });

      subscription.unsubscribe();
    });

    it('watchQuotes should emit nulls when the batch request fails, then keep polling', () => {
      const emissions: Record<string, unknown>[] = [];
      const subscription = quotes.watchQuotes(['AAPL'], 1000).subscribe((m) => emissions.push(m));

      vi.advanceTimersByTime(0);
      httpMock.expectOne(baseUrl).flush(null, { status: 503, statusText: 'Service Unavailable' });
      expect(emissions).toEqual([{ AAPL: null }]);

      vi.advanceTimersByTime(1000);
      httpMock.expectOne(baseUrl).flush({ success: true, quotes: [{ symbol: 'AAPL', price: 227.55 }] });
      expect(emissions[1]).toEqual({ AAPL: { symbol: 'AAPL', price: 227.55 } });

      subscription.unsubscribe();
    });
  });

  it('should surface 404 not found for unknown symbol', async () => {
    const fetchPromise = quotes.getQuote('INVALID');

    const request = httpMock.expectOne(`${baseUrl}/INVALID`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: false, error: 'Symbol not found' }, { status: 404, statusText: 'Not Found' });

    let rejected = false;
    try {
      await fetchPromise;
    } catch {
      rejected = true;
    }

    expect(rejected).toBeTruthy();
  });
});
