import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PriceHistory } from './price-history';

describe('PriceHistory', () => {
  let service: PriceHistory;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PriceHistory);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("reads a stock's prices for a range", async () => {
    const result = firstValueFrom(service.stock('AAPL', '1M'));

    http.expectOne('/api/quotes/AAPL/history?range=1M').flush({
      success: true, symbol: 'AAPL', range: '1M', points: [{ time: '2026-10-05T20:00:00Z', price: 231.5 }],
    });

    expect(await result).toEqual([{ time: Date.parse('2026-10-05T20:00:00Z'), value: 231.5 }]);
  });

  it("reads the portfolio's value for a range", async () => {
    const result = firstValueFrom(service.portfolio('6M'));

    http.expectOne('/api/v1/portfolio/history?range=6M').flush({
      success: true, range: '6M', points: [{ time: '2026-10-05T20:00:00Z', value: 1234.5 }],
    });

    expect(await result).toEqual([{ time: Date.parse('2026-10-05T20:00:00Z'), value: 1234.5 }]);
  });

  it("reads every stock's prices for today", async () => {
    const result = firstValueFrom(service.intraday());

    http.expectOne('/api/quotes/intraday').flush({ success: true, points: { AAPL: [230, 231.5] } });

    expect(await result).toEqual({ AAPL: [230, 231.5] });
  });
});
