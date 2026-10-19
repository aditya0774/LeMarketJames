import { Component, OnInit, inject } from '@angular/core';
import { AsyncPipe, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HoldingsService } from '@app/core/holdings/holdings.service';
import { allocation, allocationColor, portfolioTotals } from '@app/core/holdings/portfolio-totals';
import { Auth } from '@app/core/auth/auth';
import { InstrumentCatalog } from '@app/core/market/instrument-catalog';
import { HoldingDto, HoldingsResponse } from '@app/shared/models/holdings.model';
import { SignedCurrencyPipe } from '@app/shared/pipes/signed-currency.pipe';

/**
 * Holdings Display Component (the Holdings tab)
 *
 * Laid out like the redesign's Holdings board: how the money is split across stocks, then one row
 * per stock grouped into what was paid, what it is worth now and the gain or loss, with a totals
 * row. Selecting a row opens that stock's trade history.
 *
 * AC1: Unauthorized data access prevented
 * - Detects 403 errors and shows safe message: "You do not have permission to view this account."
 * - Never leaks sensitive data (account details, database errors)
 *
 * AC2: Requests scoped to authenticated user
 * - Gets accountId from Auth service (not hardcoded or passed in)
 * - Passes authenticated user context to HoldingsService
 * - Component doesn't need to know about accountId extraction
 */
@Component({
  selector: 'app-holdings-list',
  templateUrl: './holdings-list.component.html',
  styleUrl: './holdings-list.component.css',
  imports: [AsyncPipe, RouterLink, CurrencyPipe, DecimalPipe, SignedCurrencyPipe],
})
export class HoldingsListComponent implements OnInit {
  // Use getters to access service signals (avoids initialization order issues)
  get isLoading() {
    return this.holdingsService.isLoading;
  }
  
  get error() {
    return this.holdingsService.error;
  }
  
  get holdings$() {
    return this.holdingsService.holdings;
  }
  
  get currentUser() {
    return this.auth.currentUser;
  }
  
  /** The holding figures each row shows, in column order. */
  displayedColumns: string[] = ['symbol', 'quantity', 'averageCost', 'currentPrice', 'totalCost', 'currentValue', 'gainLoss', 'gainLossPercent'];

  private readonly catalog = inject(InstrumentCatalog);

  protected readonly color = allocationColor;
  protected readonly totalsOf = portfolioTotals;
  protected readonly slicesOf = allocation;

  constructor(
    private readonly holdingsService: HoldingsService,
    private readonly auth: Auth,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // Only for company names under each symbol; a failure just leaves them blank.
    this.catalog.load().catch(() => undefined);
    this.loadHoldings();
  }

  /** Largest holding first, matching the split bar above the table. */
  protected sorted(holdings: readonly HoldingDto[] | undefined): HoldingDto[] {
    return [...(holdings ?? [])].sort((a, b) => b.currentValue - a.currentValue);
  }

  protected nameFor(symbol: string): string {
    return this.catalog.bySymbol(symbol)?.name ?? '';
  }

  /**
   * AC1: Load user's holdings
   * AC2: Automatically scoped to authenticated user via HoldingsService
   * 
   * Handles all error cases:
   * - 403 ACCOUNT_ACCESS_DENIED: Shows safe message
   * - 400 Bad Request: Shows validation error
   * - 500+ Server Error: Shows generic error
   * - Network Error: Shows user-friendly message
   */
  loadHoldings(): void {
    try {
      // AC2: HoldingsService extracts accountId from Auth context
      this.holdingsService.getOwnHoldings().subscribe({
        next: (response: HoldingsResponse) => {
          // Service already updates its signals
        },
        error: (error: any) => {
          // Service error signal already updated in HoldingsService.handleError()
          // Component just displays what service provides
        }
      });
    } catch (err: any) {
      // Handle errors thrown by service (e.g., no accountId)
      // Service already set error signal
    }
  }

  /**
   * Allow user to retry after auth error
   * AC1: Safe error message shown before retry
   */
  retry(): void {
    this.loadHoldings();
  }

  /**
   * AC1: Check if error is authorization-related (403)
   * Used to show/hide retry button
   */
  isAuthError(): boolean {
    const currentError = this.error();
    return currentError?.code === 'ACCOUNT_ACCESS_DENIED';
  }

  /**
   * Navigate to the by-stock detail view when a stock row is clicked
   */
  onStockClick(symbol: string): void {
    this.router.navigate(['/holdings', symbol]);
  }
}
