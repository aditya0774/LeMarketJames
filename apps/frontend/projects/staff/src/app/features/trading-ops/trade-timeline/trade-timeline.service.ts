import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AuditEvent } from '../trade-timeline/trade-timeline';

/**
 * Service for fetching trade timeline (audit events) for an order.
 * Restricted to TRADING_OPS role via backend access control.
 */
@Injectable({
  providedIn: 'root'
})
export class TradeTimelineService {
  constructor() {}

  /**
   * Fetch the complete audit trail for an order.
   * @param orderId The order ID to fetch timeline for
   * @returns Observable of audit events in chronological order (oldest first)
   *
   * TODO: Wire to real endpoint `GET /api/v1/orders/{orderId}/timeline` once LMKT-143 lands.
   * For now, returns mocked data from contract C6.
   */
  getOrderTimeline(orderId: number): Observable<AuditEvent[]> {
    // Mock data from contract C6 (LMKT-40)
    const mockEvents: AuditEvent[] = [
      {
        eventType: 'SUBMITTED',
        occurredAt: '2026-09-21T10:30:00Z',
        details: {
          side: 'BUY',
          quantity: 10,
          price: 244.2366,
        },
      },
      {
        eventType: 'VALIDATED',
        occurredAt: '2026-09-21T10:30:01Z',
        details: {
          validationStatus: 'PASSED',
        },
      },
      {
        eventType: 'ACCEPTED',
        occurredAt: '2026-09-21T10:30:02Z',
        details: {},
      },
      {
        eventType: 'FILLED',
        occurredAt: '2026-09-21T10:30:03Z',
        details: {
          executionPrice: 244.2366,
          quantity: 10,
        },
      },
      {
        eventType: 'SETTLED',
        occurredAt: '2026-09-21T10:30:04Z',
        details: {
          cashDelta: -2442.366,
          quantityDelta: 10,
        },
      },
    ];

    return of(mockEvents);
  }
}
