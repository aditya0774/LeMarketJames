import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

export interface SegmentMetricChange { absolute: number; percent: number | null; }
export interface SegmentComparisonRow {
  segment: string;
  current: SegmentActivityRow;
  previous: SegmentActivityRow;
  activeClients: SegmentMetricChange;
  tradeCount: SegmentMetricChange;
  totalTradedValue: SegmentMetricChange;
}
export interface SegmentComparisonResponse extends SegmentActivityReportResponse {
  startDate: string;
  endDate: string;
  comparisonStartDate: string;
  comparisonEndDate: string;
  timeZone: string;
  comparison: SegmentComparisonRow[];
  allTimeData: SegmentActivityRow[];
}

/**
 * Service to fetch aggregate trading reports from the reporting-service.
 * Calls `/api/v1/reports/trades-by-stock` (ANALYST-only endpoint).
 */
@Injectable({
  providedIn: 'root'
})
export class ReportingService {

  constructor(private readonly http: HttpClient) {}

  getSegmentComparison(startDate?: string, endDate?: string,
      comparisonStartDate?: string, comparisonEndDate?: string): Observable<SegmentComparisonResponse> {
    const params: Record<string, string> = {};
    if (startDate) params['startDate'] = startDate;
    if (endDate) params['endDate'] = endDate;
    if (comparisonStartDate) params['comparisonStartDate'] = comparisonStartDate;
    if (comparisonEndDate) params['comparisonEndDate'] = comparisonEndDate;
    return this.http.get<SegmentComparisonResponse>('/api/v1/reports/activity/comparison', { params });
  }

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

    return this.http.get<SegmentActivityReportResponse>('/api/v1/reports/activity', { params });
  }
}
