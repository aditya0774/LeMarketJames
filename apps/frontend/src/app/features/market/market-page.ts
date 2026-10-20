import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { Quotes } from '../../core/quotes/quotes';
import { Watchlist } from '../../core/watchlist/watchlist';
import { Quote } from '../../shared/models/quote.model';
import { SessionExpired } from '../../shared/components/session-expired/session-expired';
import { MarketList, MarketRow } from './market-list/market-list';
import { WatchlistPanel } from './watchlist-panel/watchlist-panel';
import { MarketHistory } from '../../core/market/market-history';

/** The simulator ticks every second; 2s keeps the market graphs moving without flooding the API. */
export const MARKET_REFRESH_MS = 2000;

/**
 * The Market tab: the watchlist beside every stock with live prices, as on the redesign's Market
 * board. Polls one batch quote request for the whole market and records each stock's price trace
 * for the trend graphs in MarketHistory, which outlives this page; both panels draw from the same rows. Selecting a stock opens the
 * trade page for it.
 */
@Component({
  selector: 'app-market-page',
  imports: [MarketList, SessionExpired, WatchlistPanel],
  template: `
    @if (sessionExpired()) {
      <app-session-expired />
    }
    <div class="market-layout">
      <app-watchlist-panel
        [rows]="watchedRows()"
        [status]="watchlist.status()"
        [changeError]="watchlist.changeError()"
        (remove)="watchlist.remove($event)"
        (retry)="watchlist.load()"
      />
      <app-market-list
        [rows]="rows()"
        [watched]="watchlist.status() === 'ready' ? watchlist.watched() : null"
        (selectSymbol)="openTrade($event)"
        (toggleWatch)="watchlist.toggle($event)"
      />
    </div>
  `,
  styles: `
    .market-layout {
      display: grid;
      grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
      gap: 14px;
      align-items: start;
    }
    @media (max-width: 800px) {
      .market-layout {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class MarketPage implements OnInit {
  private readonly catalog = inject(InstrumentCatalog);
  private readonly quotes = inject(Quotes);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly watchlist = inject(Watchlist);

  private readonly latest = signal<Record<string, Quote | null>>({});
  private readonly marketHistory = inject(MarketHistory);
  protected readonly sessionExpired = signal(false);

  protected readonly rows = computed<MarketRow[]>(() =>
    this.catalog.all().map((instrument) => ({
      instrument,
      quote: this.latest()[instrument.symbol] ?? null,
      history: this.marketHistory.traces()[instrument.symbol] ?? [],
    })),
  );

  /** The watched stocks in watchlist order (newest first), skipping any no longer listed. */
  protected readonly watchedRows = computed(() => {
    const bySymbol = new Map(this.rows().map((row) => [row.instrument.symbol, row]));
    return this.watchlist.symbols().flatMap((symbol) => bySymbol.get(symbol) ?? []);
  });

  ngOnInit(): void {
    this.marketHistory.load();
    // Quotes are polled per symbol, so the stock list has to arrive first.
    this.catalog.load().then(
      () => this.watchMarket(),
      (err) => this.sessionExpired.set(err instanceof HttpErrorResponse && err.status === 401),
    );
    void this.watchlist.load();
  }

  protected openTrade(symbol: string): void {
    void this.router.navigate(['/trade'], { queryParams: { symbol } });
  }

  private watchMarket(): void {
    const symbols = this.catalog.all().map((i) => i.symbol);
    this.quotes
      .watchQuotes(symbols, MARKET_REFRESH_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((quotes) => {
        this.latest.set(quotes);
        this.marketHistory.record(quotes);
      });
  }
}
