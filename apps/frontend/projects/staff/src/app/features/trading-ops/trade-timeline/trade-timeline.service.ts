import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AuditEvent } from '../../../shared/models/audit-event.model';

/**
 * Fetches an order's timeline: its audit events, oldest first
 * (C6, `GET /api/v1/orders/{orderId}/timeline`). Restricted to the TRADING_OPS role by
 * orders-service and reached through the staff app's own origin.
 */
@Injectable({
  providedIn: 'root'
})
export class TradeTimelineService {
  private readonly http = inject(HttpClient);

  /**
   * Fetch the complete audit trail for an order.
   * @param orderId The order ID to fetch timeline for
   * @returns Observable of audit events in chronological order (oldest first)
   */
  getOrderTimeline(orderId: number): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${environment.apiBaseUrl}/api/v1/orders/${orderId}/timeline`);
  }
}
