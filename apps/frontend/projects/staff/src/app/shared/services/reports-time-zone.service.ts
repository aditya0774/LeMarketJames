import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, shareReplay, timeout } from 'rxjs/operators';

/**
 * The reports time zone (C5, lmj.reports.time-zone), read from GET /api/v1/reports/ping (C6) so the
 * app never hard-codes it. Fetched once and shared; a failed fetch is tried again on the next use.
 */
@Injectable({
  providedIn: 'root',
})
export class ReportsTimeZoneService {
  private readonly http = inject(HttpClient);

  /** The IANA ID, e.g. America/New_York. */
  readonly timeZone$: Observable<string> = this.http
    .get<{ timeZone: string }>('/api/v1/reports/ping')
    .pipe(
      // A timeout keeps a page waiting on the zone from hanging if the gateway does.
      timeout(10000),
      map((ping) => ping.timeZone),
      shareReplay(1),
    );
}
