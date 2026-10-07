import { Component, OnInit } from '@angular/core';
import { TradeTimelineComponent } from './trade-timeline/trade-timeline';
import { TradeTimelineService } from './trade-timeline/trade-timeline.service';
import { AuditEvent } from './trade-timeline/trade-timeline';

/**
 * Landing page of the TRADING_OPS role, which works on individual client trades.
 * Displays the trade timeline component; once LMKT-139 (trade search) lands,
 * the timeline will be triggered by clicking a trade search result.
 */
@Component({
  selector: 'staff-trading-ops-dashboard',
  imports: [TradeTimelineComponent],
  template: `
    <div class="dashboard">
      <h1>Trading Ops dashboard</h1>
      
      <!-- Trade search placeholder (LMKT-139) -->
      <div class="trade-search-section">
        <p class="staff-placeholder">
          Trade search will appear here when LMKT-139 lands.
          Click a trade result to view its timeline below.
        </p>
      </div>

      <!-- Trade timeline (LMKT-40) -->
      @if (selectedOrderId && events.length > 0) {
        <div class="timeline-section">
          <h2>Order Timeline #{{ selectedOrderId }}</h2>
          <staff-trade-timeline [events]="events"></staff-trade-timeline>
        </div>
      } @else if (selectedOrderId) {
        <p class="staff-placeholder">Loading timeline...</p>
      }
    </div>
  `,
  styles: [`
    .dashboard {
      padding: 16px;
    }

    .trade-search-section,
    .timeline-section {
      margin-bottom: 24px;
    }

    h2 {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 12px;
    }

    .staff-placeholder {
      color: #999;
      font-style: italic;
      padding: 12px;
      background-color: #f5f5f5;
      border-radius: 4px;
    }
  `],
})
export class TradingOpsDashboard implements OnInit {
  selectedOrderId: number | null = null;
  events: AuditEvent[] = [];

  constructor(private timelineService: TradeTimelineService) {}

  ngOnInit() {
    // TODO: Set selectedOrderId from TradeSearchComponent result click (LMKT-139)
    // For now, hardcoded to 42 to demonstrate timeline display
    this.selectOrder(42);
  }

  selectOrder(orderId: number) {
    this.selectedOrderId = orderId;
    this.timelineService.getOrderTimeline(orderId).subscribe((events) => {
      this.events = events;
    });
  }
}
