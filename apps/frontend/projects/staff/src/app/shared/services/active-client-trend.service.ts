import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { ActiveClientTrendResponse } from '../models/active-client-trend.model';

/**
 * HTTP client for the active-client trend: distinct clients who traded on each of the
 * last 30 days (C6, GET /api/v1/reports/insights/active-clients).
 */
@Injectable({
  providedIn: 'root',
})
export class ActiveClientTrendService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/reports/insights/active-clients';

  get(): Observable<ActiveClientTrendResponse> {
    // A timeout keeps the tile from loading forever if the gateway hangs.
    return this.http.get<ActiveClientTrendResponse>(this.apiUrl).pipe(timeout(10000));
  }
}
