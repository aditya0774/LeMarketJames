import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AlertList, AlertQuery } from '../../../shared/models/order-alert.model';

/**
 * Reads the surveillance alerts (C6, `GET /api/v1/surveillance/alerts`): the newest 100 that
 * match, newest first. Restricted to TRADING_OPS by staff-ops-service and routed by the staff app only.
 */
@Injectable({ providedIn: 'root' })
export class OrderAlertsService {
  private readonly http = inject(HttpClient);

  list(query: AlertQuery = {}): Observable<AlertList> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      if (value) {
        params = params.set(name, value);
      }
    }
    return this.http.get<AlertList>(`${environment.apiBaseUrl}/api/v1/surveillance/alerts`, { params });
  }
}
