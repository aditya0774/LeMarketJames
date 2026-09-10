import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { QuoteSuccessResponse } from '../../shared/models/quote.model';

@Injectable({ providedIn: 'root' })
export class Quotes {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/quotes`;

  constructor(private readonly http: HttpClient) {}

  getQuote(symbol: string): Promise<QuoteSuccessResponse> {
    return firstValueFrom(this.http.get<QuoteSuccessResponse>(`${this.baseUrl}/${symbol}`));
  }
}
