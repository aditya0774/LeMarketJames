import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { OrderResponse, OrderService } from '../../core/orders/order.service';
import { Quotes } from '../../core/quotes/quotes';
import { HoldingDto } from '../../shared/models/holdings.model';
import { Quote } from '../../shared/models/quote.model';
import { StatStrip } from './stat-strip/stat-strip';
import { MarketList, MarketRow } from './market-list/market-list';
import { appendQuotes } from './market-list/market-history';
import { PortfolioPanel, portfolioTotals } from './portfolio-panel/portfolio-panel';
import { OrdersPanel } from './orders-panel/orders-panel';
import { isOpenStatus } from './orders-panel/order-status';
import { TradeDialog } from '../trade/trade-dialog';

/** The simulator ticks every second; 2s keeps the market graphs moving without flooding the API. */
export const MARKET_REFRESH_MS = 2000;

/**
 * Dashboard Component
 *
 * Signed-in home. Container only: it loads the account's holdings and orders, polls live
 * quotes for every stock, and hands the data to presentational children. Clicking a stock
 * (market list or portfolio) opens the buy/sell popup.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, StatStrip, MarketList, PortfolioPanel, OrdersPanel, TradeDialog],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  protected readonly auth = inject(Auth);
  private readonly holdingsService = inject(HoldingsService);
  private readonly orderService = inject(OrderService);
  private readonly catalog = inject(InstrumentCatalog);
  private readonly quotes = inject(Quotes);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly holdings = signal<HoldingDto[]>([]);
  protected readonly holdingsLoading = signal(true);
  protected readonly holdingsError = signal<string | null>(null);

  protected readonly orders = signal<OrderResponse[]>([]);
  protected readonly ordersLoading = signal(true);
  protected readonly ordersError = signal<string | null>(null);

  private readonly marketQuotes = signal<Record<string, Quote | null>>({});
  protected readonly marketHistory = signal<Record<string, number[]>>({});
  protected readonly marketRows = computed<MarketRow[]>(() =>
    this.catalog.all().map((instrument) => ({
      instrument,
      quote: this.marketQuotes()[instrument.symbol] ?? null,
      history: this.marketHistory()[instrument.symbol] ?? [],
    })),
  );

  /** Symbol shown in the buy/sell popup; null when it's closed. */
  protected readonly tradeSymbol = signal<string | null>(null);

  /** Set when either request comes back 401, which means the JWT cookie has expired. */
  protected readonly sessionExpired = signal(false);

  protected readonly totals = computed(() => portfolioTotals(this.holdings()));
  protected readonly openOrders = computed(() => this.orders().filter((o) => isOpenStatus(o.orderStatus)).length);

  ngOnInit(): void {
    this.watchMarket();
    this.loadHoldings();
    this.loadOrders();
  }

  protected openTrade(symbol: string): void {
    this.tradeSymbol.set(symbol);
  }

  protected closeTrade(): void {
    this.tradeSymbol.set(null);
  }

  /** A placed order changes holdings and the order list, so reload both behind the popup. */
  protected onOrderPlaced(): void {
    this.loadHoldings();
    this.loadOrders();
  }

  protected async logout(): Promise<void> {
    try {
      await this.auth.logout();
    } finally {
      await this.router.navigate(['/']);
    }
  }

  private watchMarket(): void {
    const symbols = this.catalog.all().map((i) => i.symbol);
    this.quotes
      .watchQuotes(symbols, MARKET_REFRESH_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((quotes) => {
        this.marketQuotes.set(quotes);
        this.marketHistory.update((history) => appendQuotes(history, quotes));
      });
  }

  private loadHoldings(): void {
    try {
      this.holdingsService
        .getOwnHoldings()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response) => {
            this.holdings.set(response.holdings ?? []);
            this.holdingsError.set(null);
            this.holdingsLoading.set(false);
          },
          error: (err) => {
            // HoldingsService already maps errors to safe, user-facing messages.
            this.holdingsError.set(this.holdingsService.error()?.message ?? 'Unable to load holdings.');
            this.holdingsLoading.set(false);
            this.flagExpiredSession(err);
          },
        });
    } catch (err) {
      // getOwnHoldings throws synchronously when there is no account on the session.
      this.holdingsError.set((err as Error).message);
      this.holdingsLoading.set(false);
    }
  }

  private loadOrders(): void {
    const accountId = this.auth.currentAccountId();
    if (!accountId) {
      this.ordersError.set('No trading account is linked to this login.');
      this.ordersLoading.set(false);
      return;
    }
    this.orderService
      .getOrdersByAccountId(accountId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (orders) => {
          this.orders.set(orders ?? []);
          this.ordersError.set(null);
          this.ordersLoading.set(false);
        },
        error: (err) => {
          this.ordersError.set('Unable to load orders. Please try again.');
          this.ordersLoading.set(false);
          this.flagExpiredSession(err);
        },
      });
  }

  private flagExpiredSession(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      this.sessionExpired.set(true);
    }
  }
}
