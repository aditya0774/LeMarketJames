import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Analyst report "Trade activity by period". Placeholder: the report screen is built here. */
@Component({
  selector: 'staff-trade-activity-by-period',
  imports: [RouterLink],
  template: `
    <h1>Trade activity by period</h1>
    <p class="staff-placeholder">Placeholder. This report is not built yet.</p>
    <a routerLink="/analyst">Back to reports</a>
  `,
})
export class TradeActivityByPeriod {}
