import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, firstValueFrom, switchMap, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { QuoteSuccessResponse } from '../../shared/models/quote.model';

/** How often live quotes are refreshed. The backend simulator ticks every second. */
export const DEFAULT_QUOTE_REFRESH_MS = 5000;

@Injectable({ providedIn: 'root' })
export class Quotes {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/quotes`;

  constructor(private readonly http: HttpClient) {}

  getQuote(symbol: string): Promise<QuoteSuccessResponse> {
    return firstValueFrom(this.http.get<QuoteSuccessResponse>(`${this.baseUrl}/${symbol}`));
  }

  /**
   * Emits the quote immediately and then every `refreshMs`, so prices update as the
   * simulated market moves. Unsubscribe (e.g. on component destroy) to stop polling.
   *
   * `switchMap` cancels a still-pending request when the next one starts, so a slow
   * response can never overwrite a newer price.
   */
  watchQuote(symbol: string, refreshMs = DEFAULT_QUOTE_REFRESH_MS): Observable<QuoteSuccessResponse> {
    return timer(0, refreshMs).pipe(
      switchMap(() => this.http.get<QuoteSuccessResponse>(`${this.baseUrl}/${symbol}`)),
    );
  }
}
