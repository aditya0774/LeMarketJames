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
 * Service to fetch aggregate trading reports from the reporting-service.
 * Calls `/api/v1/reports/trades-by-stock` (ANALYST-only endpoint).
 */
@Injectable({
  providedIn: 'root'
})
export class ReportingService {

  constructor(private http: HttpClient) {}

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
}
