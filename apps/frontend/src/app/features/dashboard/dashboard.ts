import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { AccountOrders } from '../../core/orders/account-orders';
import { BalanceService } from '../../core/orders/orders.service';
import { Quotes } from '../../core/quotes/quotes';
import { HoldingDto } from '../../shared/models/holdings.model';
import { Quote } from '../../shared/models/quote.model';
import { BalanceInfo } from '../../shared/models/order.model';
import { SessionExpired } from '../../shared/components/session-expired/session-expired';
import { appendQuotes } from '../market/market-list/market-history';
import { MARKET_REFRESH_MS } from '../market/market-page';
import { StatStrip } from './stat-strip/stat-strip';
import { QuickTrade } from './quick-trade/quick-trade';
import { HoldingsSplit } from './holdings-split/holdings-split';
import { Movers } from './movers/movers';
import { PortfolioChart } from './portfolio-chart/portfolio-chart';
import { MoveMoney } from './move-money/move-money';
import { CashMovement } from '../../core/cash/cash.service';
import { portfolioTotals } from '../../core/holdings/portfolio-totals';
import { PortfolioStream } from '../../core/holdings/portfolio-stream';

/**
 * Dashboard (the Overview tab)
 *
 * Signed-in home, from the redesign's Overview board: the four headline cards, the portfolio value
 * chart, quick trade, what the user owns and today's biggest movers. Deposit and Withdraw on the
 * Buying power card open "Move money". Container only: it loads holdings, the balance and the
 * account's orders (for the open-order count, kept live by the order stream), polls every quote,
 * and hands the data to presentational children. The portfolio stream reloads holdings and the
 * balance when a fill settles (LMKT-28).
 */
@Component({
  selector: 'app-dashboard',
  imports: [SessionExpired, StatStrip, QuickTrade, HoldingsSplit, Movers, PortfolioChart, MoveMoney],
  providers: [AccountOrders],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  protected readonly auth = inject(Auth);
  private readonly holdingsService = inject(HoldingsService);
  private readonly balanceService = inject(BalanceService);
  private readonly catalog = inject(InstrumentCatalog);
  private readonly quotes = inject(Quotes);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly accountOrders = inject(AccountOrders);
  private readonly portfolioStream = inject(PortfolioStream);

  protected readonly holdings = signal<HoldingDto[]>([]);
  protected readonly holdingsLoading = signal(true);
  protected readonly holdingsError = signal<string | null>(null);
  protected readonly balance = signal<BalanceInfo | null>(null);
  /** Counts account reloads, so the portfolio chart reloads with the holdings it is built from. */
  protected readonly accountVersion = signal(0);
  /** Which way "Move money" is open, or null while it is closed. */
  protected readonly moneyDialog = signal<CashMovement | null>(null);

  protected readonly marketQuotes = signal<Record<string, Quote | null>>({});
  protected readonly marketHistory = signal<Record<string, number[]>>({});
  protected readonly instruments = computed(() => this.catalog.all());

  /** The stock loaded into quick trade: the largest holding, else the first stock listed. */
  private readonly chosenSymbol = signal<string | null>(null);
  protected readonly quickSymbol = computed(
    () => this.chosenSymbol() ?? this.holdings()[0]?.symbol ?? this.instruments()[0]?.symbol ?? '',
  );

  private readonly ownSessionExpired = signal(false);
  protected readonly sessionExpired = computed(() => this.ownSessionExpired() || this.accountOrders.sessionExpired());

  protected readonly totals = computed(() => portfolioTotals(this.holdings()));
  /** Shares held per symbol for the trade ticket; null when holdings failed to load. */
  protected readonly heldBySymbol = computed(() =>
    this.holdingsError() ? null : Object.fromEntries(this.holdings().map((h) => [h.symbol, h.quantity])),
  );

  ngOnInit(): void {
    // Quotes are polled per symbol, so the stock list has to arrive first.
    this.catalog.load().then(
      () => this.watchMarket(),
      (err) => this.flagExpiredSession(err),
    );
    this.loadHoldings();
    this.loadBalance();
    this.accountOrders.start();
    // An order moving on can change what the user owns and how much cash is left.
    this.accountOrders.statusChanged.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshAccount());
    // A settlement, or a reconnect after missing one, means holdings and cash may have changed.
    this.portfolioStream.refreshes().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshAccount());
  }

  protected chooseSymbol(symbol: string): void {
    this.chosenSymbol.set(symbol);
  }

  /** A placed order changes holdings, cash and the order list, so reload all three. */
  protected onOrderPlaced(): void {
    this.refreshAccount();
    this.accountOrders.reload();
  }

  /** The answer carries the new balance, so buying power updates without another request. */
  protected onMoneyMoved(balance: BalanceInfo): void {
    this.balance.set(balance);
  }

  private refreshAccount(): void {
    this.loadHoldings();
    this.loadBalance();
    this.accountVersion.update((v) => v + 1);
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
            this.holdings.set([...(response.holdings ?? [])].sort((a, b) => b.currentValue - a.currentValue));
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

  private loadBalance(): void {
    this.balanceService.getBalance().then(
      (response) => this.balance.set(response.success && response.balance ? response.balance : null),
      (err) => {
        this.balance.set(null);
        this.flagExpiredSession(err);
      },
    );
  }

  private flagExpiredSession(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      this.ownSessionExpired.set(true);
      this.accountOrders.flagExpiredSession(err);
    }
  }
}
