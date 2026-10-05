import { Component, input, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TradeDto } from '../../../shared/models/trade.model';

type SortableColumn = 'filledAt' | 'side' | 'quantity' | 'pricePerUnit';

/**
 * By-Stock Trades Table (Presentational Component)
 * 
 * Displays individual trades for a selected stock with client-side sorting.
 * Receives trades as @Input; no service calls.
 * 
 * Sortable columns: Date, Side, Quantity, Price, Cost (computed)
 */
@Component({
  selector: 'app-by-stock-trades-table',
  templateUrl: './by-stock-trades-table.html',
  styleUrls: ['./by-stock-trades-table.scss'],
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MatIconModule
  ]
})
export class ByStockTradesTable {
  readonly trades = input<readonly TradeDto[]>([]);
  readonly selectedSymbol = input<string | null>(null);

  // Sorting state
  protected readonly sortColumn = signal<SortableColumn | null>(null);
  protected readonly sortAscending = signal(true);

  // Computed sorted trades
  protected readonly sortedTrades = computed(() => {
    const sortCol = this.sortColumn();
    const ascending = this.sortAscending();
    
    if (!sortCol) {
      // Default: most recent first
      return [...this.trades()].sort((a, b) => {
        return new Date(b.filledAt).getTime() - new Date(a.filledAt).getTime();
      });
    }

    return [...this.trades()].sort((a, b) => {
      let aVal: any = a[sortCol];
      let bVal: any = b[sortCol];

      // Handle date sorting
      if (sortCol === 'filledAt') {
        aVal = new Date(aVal).getTime();
        bVal = new Date(bVal).getTime();
      }

      // Numeric comparison
      if (typeof aVal === 'number' && typeof bVal === 'number') {
        return ascending ? aVal - bVal : bVal - aVal;
      }

      // String comparison (side)
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return ascending ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
      }

      return 0;
    });
  });

  /**
   * Toggle sorting on a column
   */
  toggleSort(column: SortableColumn): void {
    if (this.sortColumn() === column) {
      // Toggle direction if same column
      this.sortAscending.update(val => !val);
    } else {
      // Set new column, default ascending
      this.sortColumn.set(column);
      this.sortAscending.set(true);
    }
  }

  /**
   * Get the sort indicator for a column header
   */
  getSortIndicator(column: SortableColumn): string {
    if (this.sortColumn() !== column) {
      return '';
    }
    return this.sortAscending() ? '↑' : '↓';
  }

  /**
   * Calculate cost for a trade (quantity × pricePerUnit)
   */
  calculateCost(trade: TradeDto): number {
    return trade.quantity * trade.pricePerUnit;
  }

  /**
   * Get badge class for side (BUY/SELL)
   */
  getSideBadgeClass(side: string): string {
    return side === 'BUY' ? 'badge badge-buy' : 'badge badge-sell';
  }
}
