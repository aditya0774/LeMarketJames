import { Component, Input } from '@angular/core';
import { AuditEvent } from '../../../shared/models/audit-event.model';

/**
 * Displays a chronological timeline of audit events for an order, one card per event, as on the
 * redesign's Trade search board. Used by TRADING_OPS to investigate an order's lifecycle and
 * troubleshoot fills. Times are UTC, like the trade search results above it.
 */
@Component({
  selector: 'staff-trade-timeline',
  template: `
    <ol class="trade-timeline">
      @for (event of events; track event.occurredAt + event.eventType) {
        <li class="timeline-event" [attr.data-tone]="tone(event.eventType)">
          <span class="event-bar" aria-hidden="true"></span>
          <div class="event-header">
            <span class="event-type">{{ event.eventType }}</span>
            <span class="event-time">{{ utc(event.occurredAt) }}</span>
          </div>
          <dl class="event-details">
            @for (entry of getDetailsEntries(event.details); track entry[0]) {
              <div class="detail-row">
                <dt class="detail-key">{{ entry[0] }}</dt>
                <dd class="detail-value">{{ entry[1] }}</dd>
              </div>
            }
          </dl>
        </li>
      }
    </ol>
  `,
  styles: `
    .trade-timeline {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 10px;
    }
    .timeline-event {
      --tone: var(--acc);
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px;
      border-radius: 12px;
      background: var(--bg2);
      border: 1px solid var(--l4);
    }
    .timeline-event[data-tone='done'] {
      --tone: var(--up);
    }
    .timeline-event[data-tone='settled'] {
      --tone: var(--pri);
    }
    .timeline-event[data-tone='refused'] {
      --tone: var(--down);
    }
    .event-bar {
      height: 3px;
      border-radius: 2px;
      background: var(--tone);
    }
    .event-header {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .event-type {
      font-size: var(--font-xs);
      font-weight: 600;
      letter-spacing: 0.04em;
      color: var(--tone);
    }
    .event-time {
      font-family: var(--font-mono);
      font-size: var(--font-xs);
      color: var(--dim);
    }
    .event-details {
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
      font-size: var(--font-xs);
      line-height: 1.45;
    }
    .detail-row {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .detail-key {
      color: var(--faint);
    }
    .detail-value {
      margin: 0;
      color: var(--text2);
      text-align: right;
      overflow-wrap: anywhere;
    }
  `,
})
export class TradeTimelineComponent {
  @Input() events: AuditEvent[] = [];

  getDetailsEntries(details: Record<string, any>): Array<[string, any]> {
    return Object.entries(details ?? {});
  }

  /** "2026-09-21T10:30:00Z" → "2026-09-21 10:30:00 UTC", the format the trade table uses. */
  utc(value: string): string {
    return value.replace('T', ' ').replace(/(\.\d+)?Z$/, '') + ' UTC';
  }

  /** Colour of the event: green when it filled, gold once settled, red when refused, else in progress. */
  tone(eventType: string): string {
    if (eventType === 'FILLED') {
      return 'done';
    }
    if (eventType === 'SETTLED') {
      return 'settled';
    }
    return /REJECT|REFUS|FAIL/.test(eventType) ? 'refused' : 'progress';
  }
}
