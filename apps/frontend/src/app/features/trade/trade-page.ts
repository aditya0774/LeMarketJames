import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, retry, switchMap, tap } from 'rxjs';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { BalanceService } from '../../core/orders/orders.service';
import { DEFAULT_QUOTE_REFRESH_MS, Quotes } from '../../core/quotes/quotes';
import { Quote } from '../../shared/models/quote.model';
import { SessionExpired } from '../../shared/components/session-expired/session-expired';
import { CompactNumberPipe } from '../../shared/pipes/compact-number.pipe';
import { TradeForm } from './trade-form/trade-form';
import { StockChart } from './stock-chart/stock-chart';

/**
 * The redesign's one-screen Trade page (`/trade?symbol=AAPL`): pick a stock and place a market
 * order on the left, with its live price, its price chart, today's range and the day's figures on
 * the right.
 *
 * The stock lives in the URL, so a link from the market list, a holding or the header can open
 * the page on the right stock, and the browser's back button works. Holdings and the balance are
 * loaded here and passed to the order ticket, then reloaded after each order.
 */
@Component({
  selector: 'app-trade-page',
  imports: [CurrencyPipe, DecimalPipe, SessionExpired, CompactNumberPipe, TradeForm, StockChart],
  templateUrl: './trade-page.html',
  styleUrl: './trade-page.css',
})
export class TradePage implements OnInit {
  private readonly catalog = inject(InstrumentCatalog);
  private readonly holdingsService = inject(HoldingsService);
  private readonly balanceService = inject(BalanceService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** The symbol asked for in the URL, upper-cased; empty until the route is read. */
  private readonly requested = signal('');
  protected readonly instruments = computed(() => this.catalog.all());
  protected readonly catalogLoaded = signal(false);
  /** The URL's stock, or the first stock listed when none was asked for. */
  protected readonly symbol = computed(() => this.requested() || this.instruments()[0]?.symbol || '');
  protected readonly instrument = computed(() => this.catalog.bySymbol(this.symbol()));
  /** The stock whose quote to poll: set only once it is found in the (possibly still loading) list. */
  private readonly quotedSymbol = computed(() => this.instrument()?.symbol ?? '');

  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoteError = signal(false);

  /** Shares held per symbol; null until holdings load (or if they fail to). */
  private readonly heldBySymbol = signal<Record<string, number> | null>(null);
  protected readonly held = computed(() => {
    const held = this.heldBySymbol();
    return held === null ? null : (held[this.symbol()] ?? 0);
  });
  protected readonly buyingPower = signal<number | null>(null);
  protected readonly sessionExpired = signal(false);

  protected readonly previousClose = computed(() => {
    const q = this.quote();
    return q ? q.price - q.priceChange : null;
  });
  /** Where today's price sits between the day's low (0%) and high (100%). */
  protected readonly rangePosition = computed(() => {
    const q = this.quote();
    if (!q || q.highPrice <= q.lowPrice) {
      return 50;
    }
    return Math.min(100, Math.max(0, ((q.price - q.lowPrice) / (q.highPrice - q.lowPrice)) * 100));
  });

  constructor() {
    const quotes = inject(Quotes);
    // Follow the chosen stock: poll its quote, replacing the last stock's.
    toObservable(this.quotedSymbol)
      .pipe(
        tap(() => this.resetQuote()),
        switchMap((symbol) =>
          symbol
            ? quotes.watchQuote(symbol).pipe(
                tap({ error: (err) => this.onQuoteError(err) }),
                // Keep polling through transient failures instead of freezing the panel.
                retry({ delay: DEFAULT_QUOTE_REFRESH_MS }),
              )
            : EMPTY,
        ),
        takeUntilDestroyed(),
      )
      .subscribe((response) => {
        this.quote.set(response.quote);
        this.quoteError.set(false);
      });
  }

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.requested.set((params.get('symbol') ?? '').trim().toUpperCase()));
    this.catalog.load().then(
      () => this.catalogLoaded.set(true),
      (err) => {
        this.catalogLoaded.set(true);
        this.flagExpiredSession(err);
      },
    );
    this.loadHoldings();
    this.loadBalance();
  }

  /** Picking another stock updates the URL, which the page follows. */
  protected chooseSymbol(symbol: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { symbol }, replaceUrl: true });
  }

  /** An accepted order changes holdings and cash, so reload both. */
  protected onOrderPlaced(): void {
    this.loadHoldings();
    this.loadBalance();
  }

  protected ownedLabel(symbol: string): string {
    const held = this.heldBySymbol()?.[symbol];
    return held ? ` (you own ${held})` : '';
  }

  private resetQuote(): void {
    this.quote.set(null);
    this.quoteError.set(false);
  }

  private onQuoteError(err: unknown): void {
    this.quoteError.set(true);
    this.flagExpiredSession(err);
  }

  private loadHoldings(): void {
    try {
      this.holdingsService
        .getOwnHoldings()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response) =>
            this.heldBySymbol.set(Object.fromEntries((response.holdings ?? []).map((h) => [h.symbol, h.quantity]))),
          // Leave holdings unknown; the backend still enforces the sell check.
          error: (err) => {
            this.heldBySymbol.set(null);
            this.flagExpiredSession(err);
          },
        });
    } catch {
      this.heldBySymbol.set(null);
    }
  }

  private loadBalance(): void {
    this.balanceService.getBalance().then(
      (response) => this.buyingPower.set(response.success && response.balance ? response.balance.buyingPower : null),
      (err) => {
        this.buyingPower.set(null);
        this.flagExpiredSession(err);
      },
    );
  }

  private flagExpiredSession(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      this.sessionExpired.set(true);
    }
  }
}
