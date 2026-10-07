import { Component, OnInit } from '@angular/core';
import { TradeTimelineComponent } from './trade-timeline/trade-timeline';
import { TradeSearch } from '../trade-search/trade-search';
import { TradeTimelineService } from './trade-timeline/trade-timeline.service';
import { Auth } from '../../core/auth/auth';
import { AuditEvent } from '../../shared/models/audit-event.model';

/**
 * Landing page of the TRADING_OPS role, which works on individual client trades.
 * Displays trade search (LMKT-139) and the trade timeline component (LMKT-40).
 * When a trade is selected from search results, its timeline is displayed.
 * 
 * Access control: TRADING_OPS role required (AC3).
 * Non-TRADING_OPS users see access denied message.
 */
@Component({
  selector: 'staff-trading-ops-dashboard',
  imports: [TradeSearch, TradeTimelineComponent],
  template: `
    <div class="dashboard">
      <h1>Trading Ops Dashboard</h1>
      
      @if (!hasAccess) {
        <div class="access-denied">
          <p>Access denied. You must have the TRADING_OPS role to view this page.</p>
        </div>
      } @else {
        <!-- Trade search (LMKT-139) -->
        <staff-trade-search (tradeSelected)="selectOrder($event)"></staff-trade-search>

        <!-- Trade timeline (LMKT-40) -->
        @if (selectedOrderId) {
          <div class="timeline-section">
            <h2>Order Timeline #{{ selectedOrderId }}</h2>
            <staff-trade-timeline
              [events]="events"
              [isLoading]="isLoading"
              [error]="error">
            </staff-trade-timeline>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .dashboard {
      padding: 16px;
    }

    .timeline-section {
      margin-top: 24px;
      padding-top: 24px;
      border-top: 1px solid #eee;
    }

    h2 {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 12px;
    }

    .access-denied {
      padding: 16px;
      background-color: #ffebee;
      border: 1px solid #ef5350;
      border-radius: 4px;
      color: #d32f2f;
    }
  `],
})
export class TradingOpsDashboard implements OnInit {
  hasAccess = false;
  selectedOrderId: number | null = null;
  events: AuditEvent[] = [];
  isLoading = false;
  error: string | null = null;

  constructor(
    private timelineService: TradeTimelineService,
    private auth: Auth,
  ) {}

  ngOnInit() {
    // AC3: Check TRADING_OPS role; deny CLIENT and ANALYST
    this.hasAccess = this.auth.hasRole('TRADING_OPS');
  }

  selectOrder(orderId: number) {
    this.selectedOrderId = orderId;
    this.events = [];
    this.error = null;
    this.isLoading = true;

    this.timelineService.getOrderTimeline(orderId).subscribe({
      next: (events) => {
        this.events = events;
        this.isLoading = false;
        this.error = null;
      },
      error: (error) => {
        this.isLoading = false;
        if (error.status === 403) {
          this.error = 'Access denied: TRADING_OPS role required to view this timeline';
        } else if (error.status === 404) {
          this.error = 'Order not found';
        } else {
          this.error = `Error loading timeline: ${error.statusText || 'Unknown error'}`;
        }
      },
    });
  }
}
