import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TradeService } from '../../../core/trades/trade.service';
import { Auth } from '../../../core/auth/auth';
import { TradeDto } from '../../../shared/models/trade.model';
import { ByStockTradesTable } from './by-stock-trades-table';

/**
 * By-Stock Report Container Component (LMKT-42 AC1)
 * 
 * Fetches trades for the authenticated user and provides:
 * - Stock symbol picker dropdown
 * - Filtered trades table for selected symbol
 * - Loading, error, and empty state handling
 * 
 * AC1: Unauthorized data access prevented
 * - Scoped to authenticated user (accountId from Auth service)
 * - Detects 403 errors and shows safe message
 * - No sensitive data leakage
 */
@Component({
  selector: 'app-by-stock-report',
  templateUrl: './by-stock-report.component.html',
  styleUrls: ['./by-stock-report.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    ByStockTradesTable
  ]
})
export class ByStockReportComponent implements OnInit {
  private readonly tradeService = inject(TradeService);
  private readonly auth = inject(Auth);

  // State signals
  protected readonly trades = signal<TradeDto[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly error = signal<{ message: string; code?: string } | null>(null);
  protected readonly selectedSymbol = signal<string | null>(null);

  // Computed properties
  protected readonly symbols = computed(() => {
    const uniqueSymbols = new Set(this.trades().map(t => t.symbol));
    return Array.from(uniqueSymbols).sort();
  });

  protected readonly filteredTrades = computed(() => {
    const symbol = this.selectedSymbol();
    if (!symbol) {
      return [];
    }
    return this.trades().filter(t => t.symbol === symbol);
  });

  protected readonly hasNoTrades = computed(() => !this.isLoading() && !this.error() && this.trades().length === 0);
  protected readonly hasError = computed(() => !this.isLoading() && !!this.error());

  ngOnInit(): void {
    this.loadTrades();
  }

  /**
   * Load all trades for the authenticated user
   */
  private loadTrades(): void {
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

    this.tradeService.getTrades(accountId).subscribe({
      next: (trades: TradeDto[]) => {
        this.trades.set(trades);
        this.isLoading.set(false);
        
        // Auto-select the first symbol if available
        if (this.symbols().length > 0) {
          this.selectedSymbol.set(this.symbols()[0]);
        }
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
    this.loadTrades();
  }

  /**
   * Handle symbol selection change
   */
  onSymbolChange(symbol: string): void {
    this.selectedSymbol.set(symbol);
  }
}
