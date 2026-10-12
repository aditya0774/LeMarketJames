import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * DTO for a single stock's aggregate trading activity (from reporting-service).
 */
export interface TradesByStockRow {
  symbol: string;
  totalQuantity: number;
  totalGrossAmount: number;
  buyCount: number;
  sellCount: number;
}

/**
 * Response wrapper from reporting-service.
 */
export interface TradesReportResponse {
  success: boolean;
  data: TradesByStockRow[];
}

/**
 * DTO for a single segment's aggregate trading activity (LMKT-43).
 */
export interface SegmentActivityRow {
  segment: string;
  activeClients: number;
  tradeCount: number;
  totalTradedValue: number;
}

/**
 * Response wrapper for activity by client segment report.
 */
export interface SegmentActivityReportResponse {
  success: boolean;
  data: SegmentActivityRow[];
}

const MOCK_SEGMENT_ACTIVITY_RESPONSE: SegmentActivityReportResponse = {
  success: true,
  data: [
    {
      segment: 'ACTIVE_TRADER',
      activeClients: 12,
      tradeCount: 126,
      totalTradedValue: 341250.5,
    },
    {
      segment: 'HIGH_NET_WORTH',
      activeClients: 5,
      tradeCount: 31,
      totalTradedValue: 510000,
    },
    {
      segment: 'RETAIL',
      activeClients: 42,
      tradeCount: 180,
      totalTradedValue: 215090,
    },
  ],
};

/**
 * Service to fetch aggregate trading reports from the reporting-service.
 * Calls `/api/v1/reports/trades-by-stock` (ANALYST-only endpoint).
 */
@Injectable({
  providedIn: 'root'
})
export class ReportingService {

  constructor(private readonly http: HttpClient) {}

  /**
   * Fetch aggregate trades by stock symbol for a given date range.
   *
   * @param startDate Start date (inclusive), format YYYY-MM-DD; defaults to 30 days ago if omitted
   * @param endDate End date (inclusive), format YYYY-MM-DD; defaults to today if omitted
   * @returns Observable of trades by stock, sorted alphabetically by symbol
   */
  getTradesByStock(startDate?: string, endDate?: string): Observable<TradesReportResponse> {
    let params: any = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;

    return this.http.get<TradesReportResponse>('/api/v1/reports/trades-by-stock', { params });
  }

  /**
   * Fetch aggregate trades by client segment for a given date range.
   *
   * @param startDate Start date (inclusive), format YYYY-MM-DD; defaults to 30 days ago if omitted
   * @param endDate End date (inclusive), format YYYY-MM-DD; defaults to today if omitted
   * @returns Observable of segment-level aggregate trading activity
   */
  getActivityByClientSegment(startDate?: string, endDate?: string): Observable<SegmentActivityReportResponse> {
    let params: any = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;

    return this.http
      .get<SegmentActivityReportResponse>('/api/v1/reports/activity', { params })
      .pipe(
        catchError((error) => {
          // LMKT-43 builds against a mocked API contract until the endpoint is available.
          if (error?.status === 404) {
            return of(MOCK_SEGMENT_ACTIVITY_RESPONSE);
          }
          return throwError(() => error);
        })
      );
  }
}
