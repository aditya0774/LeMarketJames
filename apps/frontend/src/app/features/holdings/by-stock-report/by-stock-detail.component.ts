import { Component, OnInit, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { signal } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TradeService } from '../../../core/trades/trade.service';
import { Auth } from '../../../core/auth/auth';
import { TradeDto } from '../../../shared/models/trade.model';
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
 * 
 * AC1: Unauthorized data access prevented
 * - Scoped to authenticated user (accountId from Auth service)
 * - Detects 403 errors and shows safe message
 * - No sensitive data leakage
 */
@Component({
  selector: 'app-by-stock-detail',
  templateUrl: './by-stock-detail.component.html',
  styleUrls: ['./by-stock-detail.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    ByStockTradesTable
  ]
})
export class ByStockDetailComponent implements OnInit {
  private readonly tradeService = inject(TradeService);
  private readonly auth = inject(Auth);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // State signals
  protected readonly trades = signal<TradeDto[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly error = signal<{ message: string; code?: string } | null>(null);
  protected readonly selectedSymbol = signal<string | null>(null);

  // Computed properties
  protected readonly hasNoTrades = computed(() => !this.isLoading() && !this.error() && this.trades().length === 0);
  protected readonly hasError = computed(() => !this.isLoading() && !!this.error());

  ngOnInit(): void {
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

  /**
   * Navigate back to the holdings list
   */
  goBackToHoldings(): void {
    this.router.navigate(['/holdings']);
  }
}
