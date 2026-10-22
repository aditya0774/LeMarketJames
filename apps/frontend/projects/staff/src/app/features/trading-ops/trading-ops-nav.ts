import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/**
 * Every Trading Ops page, listed once: the routes in app.routes.ts each carry the TRADING_OPS
 * guard, and this tab bar links them. A page added here gets its tab on every Trading Ops page.
 */
export const TRADING_OPS_PAGES = [
  { path: '/trade-search', tab: 'Trade search' },
  { path: '/alerts', tab: 'Alerts' },
] as const;

/** The tab bar at the top of each Trading Ops page. */
@Component({
  selector: 'staff-trading-ops-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="seg tabs" aria-label="Trading Ops">
      @for (page of pages; track page.path) {
        <a [routerLink]="page.path" routerLinkActive="active" ariaCurrentWhenActive="page">{{ page.tab }}</a>
      }
    </nav>
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 20px;
    }
  `,
})
export class TradingOpsNav {
  protected readonly pages = TRADING_OPS_PAGES;
}
