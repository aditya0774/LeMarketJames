import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { TradeReportResponse } from '../models/trade-report.model';

/**
 * HTTP client for trade aggregation reports.
 * Calls GET /api/v1/reports/trades with period type and optional date range.
 */
@Injectable({
  providedIn: 'root',
})
export class TradeReportService {
  private readonly apiUrl = '/api/v1/reports/trades';

  constructor(private http: HttpClient) {}

  /**
   * Fetch trade aggregation report for the given period type and optional date range.
   *
   * @param periodType - Required: 'DAY', 'WEEK', 'MONTH', or 'YEAR'
   * @param from - Optional: start date in YYYY-MM-DD format
   * @param to - Optional: end date in YYYY-MM-DD format
   * @returns Observable of TradeReportResponse
   */
  getTradeReport(
    periodType: string,
    from?: string,
    to?: string
  ): Observable<TradeReportResponse> {
    let params = new HttpParams().set('periodType', periodType);

    if (from) {
      params = params.set('from', from);
    }

    if (to) {
      params = params.set('to', to);
    }

    return this.http.get<TradeReportResponse>(this.apiUrl, { params }).pipe(
      timeout(10000) // 10 second timeout to prevent infinite loading
    );
  }
}
