import { Component, input, output } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WatchlistStatus } from '../../../core/watchlist/watchlist';
import { Sparkline } from '../../../shared/components/sparkline/sparkline';
import { MarketRow } from '../market-list/market-list';

/**
 * The Market tab's "Watchlist" from the redesign: each watched stock with its live price, today's
 * change and trend, a Trade link and Remove. Presentational: the Market page passes the watched
 * rows and the list's status, and handles removing and retrying.
 */
@Component({
  selector: 'app-watchlist-panel',
  imports: [CurrencyPipe, DecimalPipe, RouterLink, Sparkline],
  template: `
    <aside class="wl card" aria-labelledby="wl-title">
      <div class="wl-head">
        <div>
          <h2 id="wl-title">Watchlist</h2>
          <span class="sub">Stocks you're keeping an eye on</span>
        </div>
        @if (status() === 'ready') {
          <span class="mono count" [attr.aria-label]="rows().length + ' watched'">{{ rows().length }}</span>
        }
      </div>

      @if (changeError()) {
        <div class="alert err" role="alert"><div>{{ changeError() }}</div></div>
      }

      @switch (status()) {
        @case ('loading') {
          <output class="loading"><span class="spinner"></span>Chalking up…</output>
        }
        @case ('error') {
          <div class="alert err" role="alert">
            <div><b>Couldn't load your watchlist</b>Check your connection and try again.</div>
          </div>
          <button type="button" class="btn btn-sm btn-outline retry" (click)="retry.emit()">Try again</button>
        }
        @default {
          <ul>
            @for (row of rows(); track row.instrument.symbol) {
              @let q = row.quote;
              <li>
                <div class="wl-top">
                  <span class="wl-name">
                    <span class="mono sym">{{ row.instrument.symbol }}</span>
                    <span class="name">{{ row.instrument.name }}</span>
                  </span>
                  <span class="wl-price">
                    <span class="mono">{{ q ? (q.price | currency: 'USD') : '—' }}</span>
                    @if (q) {
                      <span class="mono chg" [class.up]="q.priceChange >= 0" [class.down]="q.priceChange < 0">
                        {{ q.priceChange >= 0 ? '▲' : '▼' }} {{ (q.priceChangePercent < 0 ? -q.priceChangePercent : q.priceChangePercent) | number: '1.2-2' }}%
                      </span>
                    }
                  </span>
                </div>
                <app-sparkline size="row" [values]="row.history" [label]="row.instrument.symbol + ' price today'" />
                <div class="wl-actions">
                  <a class="btn btn-soft btn-sm" routerLink="/trade" [queryParams]="{ symbol: row.instrument.symbol }">Trade</a>
                  <button type="button" class="btn btn-sm btn-outline" [attr.aria-label]="'Remove ' + row.instrument.symbol + ' from watchlist'"
                          (click)="remove.emit(row.instrument.symbol)">Remove</button>
                </div>
              </li>
            } @empty {
              <li class="wl-none">
                <svg width="28" height="28" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.9z" />
                </svg>
                <strong>Nothing on your watchlist</strong>
                <span>Tap the star next to any stock to follow its price here.</span>
              </li>
            }
          </ul>
        }
      }
    </aside>
  `,
  styleUrl: './watchlist-panel.css',
})
export class WatchlistPanel {
  readonly rows = input<readonly MarketRow[]>([]);
  readonly status = input<WatchlistStatus>('loading');
  readonly changeError = input<string | null>(null);
  readonly remove = output<string>();
  readonly retry = output<void>();
}
