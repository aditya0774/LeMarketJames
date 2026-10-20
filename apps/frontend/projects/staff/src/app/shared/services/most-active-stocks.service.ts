import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { MostActiveStocksResponse } from '../models/most-active-stocks.model';

@Injectable({ providedIn: 'root' })
export class MostActiveStocksService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/reports/insights/most-active-stocks';

  get(): Observable<MostActiveStocksResponse> {
    return this.http.get<MostActiveStocksResponse>(this.apiUrl).pipe(timeout(10000));
  }
}