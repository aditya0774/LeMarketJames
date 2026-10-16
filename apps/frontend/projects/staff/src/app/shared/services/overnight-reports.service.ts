import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { OvernightReportsResponse } from '../models/overnight-report.model';

/** HTTP client for the overnight reports list (C6, GET /api/v1/reports/overnight). */
@Injectable({
  providedIn: 'root',
})
export class OvernightReportsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/reports/overnight';

  list(): Observable<OvernightReportsResponse> {
    // A timeout keeps the page from loading forever if the gateway hangs.
    return this.http.get<OvernightReportsResponse>(this.apiUrl).pipe(timeout(10000));
  }
}
