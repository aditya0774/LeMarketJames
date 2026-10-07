import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** C6 trade search: exactly one search mode, with UTC calendar dates. */
export type TradeSearchQuery = { orderId: number } | { clientId: number; from?: string; to?: string };
export interface ClientSearchResult { clientId: number; fullName: string; }
export interface TradeSearchResult {
  orderId: number;
  clientId: number;
  accountId: number;
  instrumentId: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit: number;
  filledAt: string;
}

@Injectable({ providedIn: 'root' })
export class TradeSearchService {
  private readonly http = inject(HttpClient);

  clients(name: string) {
    return this.http.get<ClientSearchResult[]>(`${environment.apiBaseUrl}/api/v1/orders/trades/clients`, {
      params: { name },
    });
  }

  search(query: TradeSearchQuery) {
    return this.http.get<TradeSearchResult[]>(`${environment.apiBaseUrl}/api/v1/orders/trades/search`, {
      params: { ...query },
    });
  }
}
