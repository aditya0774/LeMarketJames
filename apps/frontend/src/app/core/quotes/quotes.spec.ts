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
