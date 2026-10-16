import { Component, computed, input, output } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Quote } from '../../../shared/models/quote.model';
import { Sparkline } from '../../../shared/components/sparkline/sparkline';

/** How many stocks the panel lists. */
export const MOVERS_SHOWN = 6;

/** The stocks that moved most today, either way: largest absolute percentage change first. */
export function biggestMovers(quotes: Readonly<Record<string, Quote | null>>, count = MOVERS_SHOWN): Quote[] {
  return Object.values(quotes)
    .filter((q): q is Quote => q !== null)
    .sort((a, b) => Math.abs(b.priceChangePercent) - Math.abs(a.priceChangePercent))
    .slice(0, count);
}

/**
 * "Today's biggest movers" from the redesign's Overview, built from the same live quotes as the
 * Market tab. Selecting a stock asks the page to load it into quick trade.
 */
@Component({
  selector: 'app-movers',
  imports: [CurrencyPipe, DecimalPipe, RouterLink, Sparkline],
  template: `
    <section class="movers card" aria-labelledby="movers-title">
      <div class="movers-head">
        <h2 id="movers-title">Today's biggest movers</h2>
        <a class="btn-link" routerLink="/market">All {{ total() }} →</a>
      </div>
      @if (movers().length) {
        <ul>
          @for (q of movers(); track q.symbol) {
            <li>
              <button type="button" [attr.aria-label]="'Load ' + q.symbol + ' into quick trade'" (click)="selectSymbol.emit(q.symbol)">
                <span class="sym">{{ q.symbol }}</span>
                <app-sparkline size="row" [values]="history()[q.symbol] ?? []" [label]="q.symbol + ' price today'" />
                <span class="mono price">{{ q.price | currency: 'USD' }}</span>
                <span class="mono chg" [class.up]="q.priceChange >= 0" [class.down]="q.priceChange < 0">
                  {{ q.priceChange >= 0 ? '+' : '−' }}{{ (q.priceChangePercent < 0 ? -q.priceChangePercent : q.priceChangePercent) | number: '1.2-2' }}%
                </span>
              </button>
            </li>
          }
        </ul>
      } @else {
        <output class="loading"><span class="spinner"></span>Waiting for live prices…</output>
      }
    </section>
  `,
  styles: `
    .movers {
      display: flex;
      flex-direction: column;
      gap: 10px;
      height: 100%;
      padding: 16px 18px;
    }
    .movers-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .movers-head h2 {
      font-size: var(--font-md);
      letter-spacing: 0;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    button {
      width: 100%;
      height: 36px;
      padding: 0 8px;
      display: flex;
      align-items: center;
      gap: 10px;
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      color: var(--text);
      font: 400 var(--font-sm) var(--font-body);
      cursor: pointer;
    }
    button:hover {
      background: var(--card2);
    }
    .sym {
      width: 46px;
      text-align: left;
    }
    .price {
      margin-left: auto;
    }
    .chg {
      width: 64px;
      text-align: right;
    }
    .loading {
      padding: 8px 0;
    }
  `,
})
export class Movers {
  readonly quotes = input<Readonly<Record<string, Quote | null>>>({});
  readonly history = input<Readonly<Record<string, readonly number[]>>>({});
  readonly selectSymbol = output<string>();

  protected readonly movers = computed(() => biggestMovers(this.quotes()));
  protected readonly total = computed(() => Object.keys(this.quotes()).length);
}
