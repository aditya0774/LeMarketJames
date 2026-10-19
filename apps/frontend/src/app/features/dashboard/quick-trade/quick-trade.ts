import { Component, computed, input, model, output } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Instrument } from '../../../core/market/instrument-catalog';
import { Quote } from '../../../shared/models/quote.model';
import { TradeForm } from '../../trade/trade-form/trade-form';

/**
 * The Overview's "Quick trade" panel: pick a stock, see its live price, and place a market order
 * without leaving the page. The stock can also be set from outside (the holdings and movers
 * lists load a stock into it), hence the two-way `symbol`.
 */
@Component({
  selector: 'app-quick-trade',
  imports: [CurrencyPipe, DecimalPipe, TradeForm],
  template: `
    <section class="qt" aria-labelledby="qt-title">
      <div class="qt-head">
        <h2 id="qt-title">Quick trade</h2>
        <span class="faint">Select any stock to load it</span>
      </div>
      <label class="qt-pick">
        Stock
        <select class="in" [value]="symbol()" (change)="symbol.set($any($event.target).value)">
          @for (i of instruments(); track i.symbol) {
            <option [value]="i.symbol" [selected]="i.symbol === symbol()">{{ i.symbol }} · {{ i.name }}{{ i.tradable ? '' : ' (not tradable)' }}</option>
          }
        </select>
      </label>
      <div class="qt-price">
        @if (quote(); as q) {
          <span>
            <span class="mono qt-now">{{ q.price | currency: 'USD' }}</span>
            <span class="mono" [class.up]="q.priceChange >= 0" [class.down]="q.priceChange < 0">
              {{ q.priceChange >= 0 ? '▲' : '▼' }} {{ (q.priceChangePercent < 0 ? -q.priceChangePercent : q.priceChangePercent) | number: '1.2-2' }}% today
            </span>
          </span>
        } @else {
          <span class="faint">Waiting for the live price…</span>
        }
      </div>
      <app-trade-form
        [instrument]="instrument()"
        [quote]="quote()"
        [held]="held()"
        [buyingPower]="buyingPower()"
        (orderPlaced)="orderPlaced.emit()"
      />
    </section>
  `,
  styles: `
    .qt {
      display: flex;
      flex-direction: column;
      gap: 12px;
      height: 100%;
      padding: 18px;
      background: var(--card);
      border: 1px solid var(--accline);
      border-radius: var(--radius-lg);
    }
    .qt-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: var(--font-xs);
    }
    .qt-head h2 {
      font-size: var(--font-md);
      letter-spacing: 0;
    }
    .qt-pick {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: var(--font-xs);
      color: var(--dim);
    }
    .qt-pick select {
      font-weight: 500;
    }
    .qt-price {
      font-size: 12.5px;
    }
    .qt-now {
      margin-right: 6px;
      font-size: 22px;
      font-weight: 500;
    }
  `,
})
export class QuickTrade {
  readonly instruments = input<readonly Instrument[]>([]);
  readonly quotes = input<Readonly<Record<string, Quote | null>>>({});
  /** Shares held per symbol; null when holdings couldn't be loaded. */
  readonly holdings = input<Readonly<Record<string, number>> | null>(null);
  readonly buyingPower = input<number | null>(null);
  readonly symbol = model('');
  readonly orderPlaced = output<void>();

  protected readonly instrument = computed(() => this.instruments().find((i) => i.symbol === this.symbol()));
  protected readonly quote = computed(() => this.quotes()[this.symbol()] ?? null);
  protected readonly held = computed(() => {
    const holdings = this.holdings();
    return holdings === null ? null : (holdings[this.symbol()] ?? 0);
  });
}
