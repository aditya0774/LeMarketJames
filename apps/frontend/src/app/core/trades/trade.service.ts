import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TradeDto } from '../../shared/models/trade.model';

/**
 * Service for fetching trade history data from the backend.
 * Trades represent filled orders with price execution details.
 */
@Injectable({
  providedIn: 'root'
})
export class TradeService {
  private readonly apiUrl = `${environment.apiBaseUrl}/api/v1/trades`;

  constructor(private http: HttpClient) {}

  /**
   * Fetch all filled trades for a given account.
   * @param accountId The account ID to fetch trades for
   * @returns Observable of trade DTOs
   */
  getTrades(accountId: number): Observable<TradeDto[]> {
    return this.http.get<TradeDto[]>(this.apiUrl, {
      params: { accountId: accountId.toString() }
    });
  }

  /**
   * Fetch trades for a specific symbol (client-side filtered).
   * Fetches all trades and filters by symbol in the component.
   * @param accountId The account ID to fetch trades for
   * @param symbol The stock symbol to filter by
   * @returns Observable of trade DTOs for the specified symbol
   */
  getTradesBySymbol(accountId: number, symbol: string): Observable<TradeDto[]> {
    return this.http.get<TradeDto[]>(this.apiUrl, {
      params: { accountId: accountId.toString(), symbol }
    });
  }
}
