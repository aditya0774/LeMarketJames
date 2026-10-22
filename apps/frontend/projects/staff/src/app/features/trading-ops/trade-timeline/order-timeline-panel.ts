import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuditEvent } from '../../../shared/models/audit-event.model';
import { TradeTimelineComponent } from './trade-timeline';
import { TradeTimelineService } from './trade-timeline.service';

/**
 * One order's timeline in a card: it loads the order's audit events whenever `orderId` changes
 * and shows them with {@link TradeTimelineComponent}, or why it could not. Lets a page link an
 * order to its timeline without loading the trail itself (the alerts page).
 */
@Component({
  selector: 'staff-order-timeline-panel',
  imports: [TradeTimelineComponent],
  template: `
    <section class="card panel" aria-label="Order timeline">
      @if (orderId(); as id) {
        <div class="head">
          <h2>Order timeline <span class="mono">#{{ id }}</span></h2>
          <span>From the audit trail · oldest first</span>
        </div>
        @if (loading()) {
          <output class="loading"><span class="spinner"></span>Loading the timeline…</output>
        } @else if (error()) {
          <div class="alert err" role="alert"><div>{{ error() }}</div></div>
        } @else if (events().length) {
          <staff-trade-timeline [events]="events()" />
        } @else {
          <p class="hint">No audit events are recorded for this order.</p>
        }
      } @else {
        <p class="hint">{{ prompt() }}</p>
      }
    </section>
  `,
  styles: `
    .panel {
      display: flex;
      flex-direction: column;
      gap: 14px;
      padding: 18px 20px;
    }
    .head {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 8px;
    }
    .head h2 {
      font-size: var(--font-md);
      letter-spacing: 0;
    }
    .head h2 .mono {
      color: var(--pri);
    }
    .head span:not(.mono) {
      font-size: 12.5px;
      color: var(--faint);
    }
    .loading {
      padding: 8px 0;
    }
    .hint,
    .alert {
      margin: 0;
    }
  `,
})
export class OrderTimelinePanel {
  private readonly timelines = inject(TradeTimelineService);

  /** The order to show; null shows `prompt`. */
  readonly orderId = input<number | null>(null);
  /** What to say before an order is chosen. */
  readonly prompt = input('Select an order to see its timeline from the audit trail.');

  protected readonly events = signal<AuditEvent[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  private request?: Subscription;

  constructor() {
    // A newer choice replaces a timeline still loading, so a slow answer never shows for the wrong order.
    effect(() => this.load(this.orderId()));
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  private load(orderId: number | null): void {
    this.request?.unsubscribe();
    this.events.set([]);
    this.error.set('');
    this.loading.set(orderId !== null);
    if (orderId === null) {
      return;
    }
    this.request = this.timelines.getOrderTimeline(orderId).subscribe({
      next: (events) => { this.events.set(events); this.loading.set(false); },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(error.status === 403 ? 'Access denied. Order timelines require Trading Operations.'
          : error.status === 401 ? 'Your session has expired. Please sign in again.'
          : 'Unable to load this order timeline. Please try again.');
      },
    });
  }
}
