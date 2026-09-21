import { Component, input } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { PortfolioTotals } from '../portfolio-panel/portfolio-panel';
import { SignedCurrencyPipe } from '../../../shared/pipes/signed-currency.pipe';

/**
 * The four headline cards. Buying power stays "—" until the backend implements
 * GET /api/balance; the mockup's "Day P/L" is shown as total unrealised P/L because
 * no day-open prices are exposed yet (see API-CONTRACTS.md, "Dashboard & Trade").
 */
@Component({
  selector: 'app-stat-strip',
  imports: [CurrencyPipe, DecimalPipe, SignedCurrencyPipe],
  template: `
    <div class="stat-strip">
      <div class="stat-card">
        <div class="lbl">Buying power</div>
        <div class="val tabular">{{ buyingPower() != null ? (buyingPower() | currency: 'USD') : '—' }}</div>
        <div class="delta">{{ buyingPower() != null ? 'Available now' : 'Balance unavailable' }}</div>
      </div>
      <div class="stat-card">
        <div class="lbl">Portfolio value</div>
        <div class="val tabular">{{ totals().value | currency: 'USD' }}</div>
        <div class="delta">Market value of holdings</div>
      </div>
      <div class="stat-card">
        <div class="lbl">Total P/L</div>
        <div class="val tabular" [class.up]="totals().gainLoss > 0" [class.down]="totals().gainLoss < 0">
          {{ totals().gainLossPercent >= 0 ? '+' : '' }}{{ totals().gainLossPercent | number: '1.2-2' }}%
        </div>
        <div class="delta" [class.up]="totals().gainLoss > 0" [class.down]="totals().gainLoss < 0">
          {{ totals().gainLoss >= 0 ? '▲' : '▼' }} {{ totals().gainLoss | signedCurrency }} unrealised
        </div>
      </div>
      <div class="stat-card">
        <div class="lbl">Open orders</div>
        <div class="val tabular">{{ openOrders() }}</div>
        <div class="delta">Pending execution</div>
      </div>
    </div>
  `,
})
export class StatStrip {
  readonly totals = input.required<PortfolioTotals>();
  readonly openOrders = input(0);
  readonly buyingPower = input<number | null>(null);
}
