import { Component, OnInit, inject, computed, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TradeService } from '../../../core/trades/trade.service';
import { HoldingsService } from '../../../core/holdings/holdings.service';
import { InstrumentCatalog } from '../../../core/market/instrument-catalog';
import { Auth } from '../../../core/auth/auth';
import { HoldingDto } from '../../../shared/models/holdings.model';
import { TradeDto } from '../../../shared/models/trade.model';
import { SignedCurrencyPipe } from '../../../shared/pipes/signed-currency.pipe';
import { ByStockTradesTable } from './by-stock-trades-table';

/**
 * By-Stock Detail Component (LMKT-42 AC1)
 * 
 * Route-based detail view: user clicks a stock in Holdings table → navigates to /holdings/:symbol
 * This component fetches and displays trades for that specific stock.
 * 
 * Features:
 * - Extract symbol from route parameter
 * - Load trades for selected symbol
 * - Display trades in sortable table
 * - Back navigation to holdings list
 * - Error and loading state handling
 * - From the redesign's Holding board: the position summary (shares, average paid, worth now,
 *   gain/loss), a "Trade" button for this stock and a switcher between the stocks the user owns.
 *   These come from the holdings list; if it can't be loaded they are simply left out.
 * 
 * AC1: Unauthorized data access prevented
 * - Scoped to authenticated user (accountId from Auth service)
 * - Detects 403 errors and shows safe message
 * - No sensitive data leakage
 */
@Component({
  selector: 'app-by-stock-detail',
  templateUrl: './by-stock-detail.component.html',
  styleUrl: './by-stock-detail.component.css',
  imports: [CurrencyPipe, DecimalPipe, SignedCurrencyPipe, ByStockTradesTable]
})
export class ByStockDetailComponent implements OnInit {
  private readonly tradeService = inject(TradeService);
  private readonly auth = inject(Auth);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly holdingsService = inject(HoldingsService);
  private readonly catalog = inject(InstrumentCatalog);

  // State signals
  protected readonly trades = signal<TradeDto[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly error = signal<{ message: string; code?: string } | null>(null);
  protected readonly selectedSymbol = signal<string | null>(null);

  // Computed properties
  protected readonly hasNoTrades = computed(() => !this.isLoading() && !this.error() && this.trades().length === 0);
  protected readonly hasError = computed(() => !this.isLoading() && !!this.error());

  private readonly holdings = signal<HoldingDto[]>([]);
  /** This stock's line from the holdings list, for the summary; null if not held or not loaded. */
  protected readonly position = computed(() => this.holdings().find((h) => h.symbol === this.selectedSymbol()) ?? null);
  protected readonly ownedSymbols = computed(() => this.holdings().map((h) => h.symbol).sort());
  protected readonly companyName = computed(() => this.nameOf(this.selectedSymbol() ?? ''));

  ngOnInit(): void {
    this.catalog.load().catch(() => undefined);
    this.loadHoldings();
    // Extract symbol from route parameter and load trades
    this.route.params.subscribe(params => {
      const symbol = params['symbol'];
      if (symbol) {
        this.selectedSymbol.set(symbol.toUpperCase());
        this.loadTrades(symbol.toUpperCase());
      }
    });
  }

  /**
   * Load trades for the selected stock
   */
  private loadTrades(symbol: string): void {
    const accountId = this.auth.currentAccountId();
    if (!accountId) {
      this.error.set({
        message: 'Unable to load your account information.',
        code: 'NO_ACCOUNT'
      });
      return;
    }

    this.isLoading.set(true);
    this.error.set(null);

    this.tradeService.getTradesBySymbol(accountId, symbol).subscribe({
      next: (trades: TradeDto[]) => {
        this.trades.set(trades);
        this.isLoading.set(false);
      },
      error: (error: any) => {
        this.isLoading.set(false);
        this.handleError(error);
      }
    });
  }

  /**
   * Handle errors from the trade service
   */
  private handleError(error: any): void {
    const status = error?.status;
    const message = error?.error?.message || error?.message || 'Failed to load trades.';
    
    if (status === 403) {
      this.error.set({
        message: 'You do not have permission to view this account.',
        code: 'ACCOUNT_ACCESS_DENIED'
      });
    } else if (status === 400) {
      this.error.set({
        message: message,
        code: 'BAD_REQUEST'
      });
    } else if (status >= 500) {
      this.error.set({
        message: 'Server error. Please try again later.',
        code: 'SERVER_ERROR'
      });
    } else {
      this.error.set({
        message: message,
        code: 'NETWORK_ERROR'
      });
    }
  }

  /**
   * Retry loading trades after an error
   */
  retry(): void {
    const symbol = this.selectedSymbol();
    if (symbol) {
      this.loadTrades(symbol);
    }
  }

  protected nameOf(symbol: string): string {
    return this.catalog.bySymbol(symbol)?.name ?? '';
  }

  /** Opens the trade page with this stock already chosen. */
  protected tradeThisStock(): void {
    void this.router.navigate(['/trade'], { queryParams: { symbol: this.selectedSymbol() } });
  }

  protected switchStock(symbol: string): void {
    void this.router.navigate(['/holdings', symbol]);
  }

  private loadHoldings(): void {
    try {
      this.holdingsService.getOwnHoldings().subscribe({
        next: (response) => this.holdings.set(response.holdings ?? []),
        // The summary and switcher are extras; the trade history still shows without them.
        error: () => this.holdings.set([]),
      });
    } catch {
      this.holdings.set([]);
    }
  }

  /**
   * Navigate back to the holdings list
   */
  goBackToHoldings(): void {
    this.router.navigate(['/holdings']);
  }
}
