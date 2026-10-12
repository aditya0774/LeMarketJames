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
      @if (isLoading) {
        <div class="loading-state">
          <div class="spinner"></div>
          <p>Loading timeline...</p>
        </div>
      } @else if (error) {
        <div class="error-state">
          <p class="error-message">{{ error }}</p>
        </div>
      } @else if (events.length > 0) {
        @for (event of events; track event.occurredAt) {
          <div class="timeline-event">
            <div class="event-header">
              <span class="event-type">{{ event.eventType }}</span>
              <span class="event-time">{{ event.occurredAt | date: 'yyyy-MM-dd HH:mm:ss':'UTC' }} UTC</span>
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
      } @else {
        <p class="empty-state">No events found</p>
      }
    </div>
  `,
  styles: [`
    .trade-timeline {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 24px;
      color: var(--text-dim);
      background: var(--ink-2);
      border: 1px solid var(--purple-line);
      border-radius: 8px;
    }

    .spinner {
      border: 3px solid color-mix(in srgb, var(--purple-line) 80%, transparent);
      border-top: 3px solid var(--gold);
      border-radius: 50%;
      width: 24px;
      height: 24px;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    .error-state {
      padding: 16px;
      background: color-mix(in srgb, var(--down) 12%, var(--ink-2));
      border: 1px solid color-mix(in srgb, var(--down) 60%, var(--purple-line));
      border-radius: 4px;
      color: var(--text);
    }

    .error-message {
      margin: 0;
      font-size: 14px;
    }

    .empty-state {
      padding: 16px;
      color: var(--text-dim);
      font-style: italic;
      text-align: center;
      margin: 0;
      background: var(--ink-2);
      border: 1px solid var(--purple-line);
      border-radius: 8px;
    }

    .timeline-event {
      padding: 14px;
      border: 1px solid var(--purple-line);
      border-radius: 8px;
      background: color-mix(in srgb, var(--ink) 82%, black);
    }

    .event-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 8px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--purple-line);
    }

    .event-type {
      font-weight: 700;
      font-size: 13px;
      letter-spacing: 0.04em;
      color: var(--gold);
    }

    .event-time {
      font-size: 12px;
      color: var(--text-dim);
    }

    .event-details {
      display: flex;
      flex-direction: column;
      gap: 8px;
      font-size: 13px;
    }

    .detail-row {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 10px;
      align-items: start;
    }

    .detail-key {
      color: var(--text-dim);
      font-weight: 500;
    }

    .detail-value {
      color: var(--text);
      white-space: normal;
      overflow-wrap: anywhere;
      word-break: break-word;
      text-align: right;
      line-height: 1.35;
    }
  `],
})
export class TradeTimelineComponent {
  @Input() events: AuditEvent[] = [];
  @Input() isLoading = false;
  @Input() error: string | null = null;

  getDetailsEntries(details: Record<string, any> | null | undefined): Array<[string, string]> {
    if (!details || typeof details !== 'object' || Array.isArray(details)) return [];
    return Object.entries(details).map(([key, value]) => [key, this.stringifyDetail(value)]);
  }

  private stringifyDetail(value: unknown): string {
    if (value === null || value === undefined) return '';
    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') return String(value);
    if (Array.isArray(value)) return value.map(item => String(item)).join(', ');
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
}
