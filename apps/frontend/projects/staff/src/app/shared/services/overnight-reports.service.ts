import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { OvernightReportsResponse } from '../models/overnight-report.model';

/** HTTP client for the overnight reports list (C6, GET /api/v1/reports/overnight) and where a run's file is. */
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

  /**
   * Where a run's file downloads from (BR-16). A plain link, not a request from here: the browser
   * saves the attachment itself, and the session cookie goes with it as with any same-origin call.
   */
  outputUrl(runId: string): string {
    return `${this.apiUrl}/${encodeURIComponent(runId)}/output`;
  }
}
