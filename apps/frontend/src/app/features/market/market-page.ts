import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { Quotes } from '../../core/quotes/quotes';
import { Quote } from '../../shared/models/quote.model';
import { SessionExpired } from '../../shared/components/session-expired/session-expired';
import { MarketList, MarketRow } from './market-list/market-list';
import { appendQuotes } from './market-list/market-history';

/** The simulator ticks every second; 2s keeps the market graphs moving without flooding the API. */
export const MARKET_REFRESH_MS = 2000;

/**
 * The Market tab: every stock with live prices. Polls one batch quote request for the whole
 * market and keeps a short price trace per stock for the trend graphs. Selecting a stock opens
 * the trade page for it.
 */
@Component({
  selector: 'app-market-page',
  imports: [MarketList, SessionExpired],
  template: `
    @if (sessionExpired()) {
      <app-session-expired />
    }
    <app-market-list [rows]="rows()" (selectSymbol)="openTrade($event)" />
  `,
})
export class MarketPage implements OnInit {
  private readonly catalog = inject(InstrumentCatalog);
  private readonly quotes = inject(Quotes);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly latest = signal<Record<string, Quote | null>>({});
  private readonly history = signal<Record<string, number[]>>({});
  protected readonly sessionExpired = signal(false);

  protected readonly rows = computed<MarketRow[]>(() =>
    this.catalog.all().map((instrument) => ({
      instrument,
      quote: this.latest()[instrument.symbol] ?? null,
      history: this.history()[instrument.symbol] ?? [],
    })),
  );

  ngOnInit(): void {
    // Quotes are polled per symbol, so the stock list has to arrive first.
    this.catalog.load().then(
      () => this.watchMarket(),
      (err) => this.sessionExpired.set(err instanceof HttpErrorResponse && err.status === 401),
    );
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
        this.history.update((h) => appendQuotes(h, quotes));
      });
  }
}
