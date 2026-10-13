import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { VolumeInsightPeriodType, VolumeInsightResponse } from '../models/volume-insight.model';

/**
 * HTTP client for the volume insight: the last completed period's traded volume
 * compared with the period before it (C6, GET /api/v1/reports/insights/volume).
 */
@Injectable({
  providedIn: 'root',
})
export class VolumeInsightService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/reports/insights/volume';

  get(periodType: VolumeInsightPeriodType): Observable<VolumeInsightResponse> {
    const params = new HttpParams().set('periodType', periodType);
    // A timeout keeps the tile from loading forever if the gateway hangs.
    return this.http.get<VolumeInsightResponse>(this.apiUrl, { params }).pipe(timeout(10000));
  }
}
