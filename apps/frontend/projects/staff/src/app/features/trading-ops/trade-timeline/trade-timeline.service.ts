import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditEvent } from '../../../shared/models/audit-event.model';

/**
 * Service for fetching trade timeline (audit events) for an order.
 * Restricted to TRADING_OPS role via backend access control.
 */
@Injectable({
  providedIn: 'root'
})
export class TradeTimelineService {
  private readonly apiUrl = '/api/v1/orders';

  constructor(private http: HttpClient) {}

  /**
   * Fetch the complete audit trail for an order from the backend.
   * @param orderId The order ID to fetch timeline for
   * @returns Observable of audit events in chronological order (oldest first)
   *
   * GET /api/v1/orders/{orderId}/timeline
   * - 200: array of audit events
   * - 403: not TRADING_OPS role or order not found
   * - 401: not authenticated
   */
  getOrderTimeline(orderId: number): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${this.apiUrl}/${orderId}/timeline`);
  }
}
