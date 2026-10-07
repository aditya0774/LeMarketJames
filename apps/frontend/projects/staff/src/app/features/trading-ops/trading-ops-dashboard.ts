import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Landing page of the TRADING_OPS role, which works on individual client trades.
 */
@Component({
  selector: 'staff-trading-ops-dashboard',
  imports: [RouterLink],
  template: `
    <h1>Trading Ops dashboard</h1>
    <nav>
      <a routerLink="/trade-search">Trade search</a>
    </nav>
  `,
})
export class TradingOpsDashboard {}
