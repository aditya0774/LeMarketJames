import { Component, Input } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { AuditEvent } from '../../../shared/models/audit-event.model';

/**
 * Displays a chronological timeline of audit events for an order.
 * Used by TRADING_OPS to investigate order lifecycle and troubleshoot fills.
 */
@Component({
  selector: 'staff-trade-timeline',
  imports: [CommonModule, DatePipe],
  template: `
    <div class="trade-timeline">
      @for (event of events; track event.occurredAt) {
        <div class="timeline-event">
          <div class="event-header">
            <span class="event-type">{{ event.eventType }}</span>
            <span class="event-time">{{ event.occurredAt | date: 'short' }}</span>
          </div>
          <div class="event-details">
            @for (entry of getDetailsEntries(event.details); track entry[0]) {
              <div class="detail-row">
                <span class="detail-key">{{ entry[0] }}:</span>
                <span class="detail-value">{{ entry[1] }}</span>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .trade-timeline {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .timeline-event {
      padding: 12px;
      border: 1px solid #e0e0e0;
      border-radius: 4px;
      background-color: #fafafa;
    }

    .event-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      padding-bottom: 8px;
      border-bottom: 1px solid #f0f0f0;
    }

    .event-type {
      font-weight: 500;
      font-size: 14px;
      color: #333;
    }

    .event-time {
      font-size: 12px;
      color: #666;
    }

    .event-details {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 13px;
    }

    .detail-row {
      display: flex;
      justify-content: space-between;
    }

    .detail-key {
      color: #666;
      font-weight: 500;
    }

    .detail-value {
      color: #333;
    }
  `],
})
export class TradeTimelineComponent {
  @Input() events: AuditEvent[] = [];

  getDetailsEntries(details: Record<string, any>): Array<[string, any]> {
    return Object.entries(details);
  }
}
