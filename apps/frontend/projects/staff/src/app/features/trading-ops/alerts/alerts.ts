import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ALERT_REASONS, AlertList, AlertQuery, AlertReason } from '../../../shared/models/order-alert.model';
import { OrderTimelinePanel } from '../trade-timeline/order-timeline-panel';
import { TradingOpsNav } from '../trading-ops-nav';
import { OrderAlertsService } from './order-alerts.service';

/**
 * Trading Ops' alerts (BR-17): orders brought to their attention by surveillance, newest first,
 * narrowed by reason and by UTC days like trade search. Each order links to its timeline from the
 * audit trail, shown below the list.
 */
@Component({
  selector: 'staff-alerts',
  imports: [DecimalPipe, FormsModule, OrderTimelinePanel, TradingOpsNav],
  templateUrl: './alerts.html',
  styleUrl: './alerts.css',
})
export class Alerts {
  private readonly api = inject(OrderAlertsService);
  private request?: Subscription;

  protected readonly reasons = Object.entries(ALERT_REASONS) as [AlertReason, string][];

  reason: AlertReason | '' = '';
  from = '';
  to = '';

  readonly data = signal<AlertList | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  /** The order whose timeline is shown; null until one is chosen in the list. */
  readonly selectedOrder = signal<number | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
    this.load({});
  }

  /** Loads the list for the filters in the form. */
  apply(): void {
    if (this.from && this.to && this.to < this.from) {
      this.error.set('End date must be on or after start date.');
      return;
    }
    this.load({ reason: this.reason || undefined, from: this.from || undefined, to: this.to || undefined });
  }

  clear(): void {
    this.reason = '';
    this.from = '';
    this.to = '';
    this.apply();
  }

  label(reason: AlertReason): string {
    return ALERT_REASONS[reason] ?? reason;
  }

  /** To the second: the server sends microseconds, which no one reads. */
  utcTime(value: string): string {
    return value.slice(0, 19).replace('T', ' ') + ' UTC';
  }

  private load(query: AlertQuery): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set('');
    this.selectedOrder.set(null);
    this.request = this.api.list(query).subscribe({
      next: (data) => { this.data.set(data); this.loading.set(false); },
      error: (error: HttpErrorResponse) => {
        this.data.set(null);
        this.loading.set(false);
        this.error.set(error.status === 403 ? 'Access denied. Alerts require Trading Operations.'
          : error.status === 401 ? 'Your session has expired. Please sign in again.'
          : error.status === 400 ? 'Check the filters and try again.'
          : 'Unable to load alerts. Please try again.');
      },
    });
  }
}
