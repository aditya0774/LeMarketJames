import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ChartPoint,
  ChartRange,
  PortfolioHistoryResponse,
  QuoteHistoryResponse,
} from '../../shared/models/chart.model';

/**
 * Past prices for the charts: one stock's and the signed-in user's stocks as a whole, both from
 * trading-service. Both come back as {@link ChartPoint}s so one chart component draws either.
 */
@Injectable({ providedIn: 'root' })
export class PriceHistory {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  stock(symbol: string, range: ChartRange): Observable<ChartPoint[]> {
    return this.http
      .get<QuoteHistoryResponse>(`${this.baseUrl}/api/quotes/${encodeURIComponent(symbol)}/history`, {
        params: { range },
      })
      .pipe(map((response) => (response.points ?? []).map((p) => ({ time: Date.parse(p.time), value: p.price }))));
  }

  portfolio(range: ChartRange): Observable<ChartPoint[]> {
    return this.http
      .get<PortfolioHistoryResponse>(`${this.baseUrl}/api/v1/portfolio/history`, { params: { range } })
      .pipe(map((response) => (response.points ?? []).map((p) => ({ time: Date.parse(p.time), value: p.value }))));
  }
}
