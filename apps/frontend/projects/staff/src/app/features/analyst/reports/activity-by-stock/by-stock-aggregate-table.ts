import { Component, input, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TradesByStockRow } from '@staff/core/reports/reporting.service';

type SortableColumn = 'symbol' | 'totalQuantity' | 'totalGrossAmount' | 'buyCount' | 'sellCount';

/**
 * By-Stock Aggregate Table (Presentational Component)
 *
 * Displays aggregate trading activity by stock symbol with client-side sorting.
 * Receives trades data as @Input; no service calls.
 *
 * Sortable columns: Symbol, Total Quantity, Total Gross Amount, Buy Count, Sell Count
 * Default sort: Highest total gross amount first (descending)
 */
@Component({
  selector: 'staff-by-stock-aggregate-table',
  templateUrl: './by-stock-aggregate-table.html',
  styleUrls: ['./by-stock-aggregate-table.scss'],
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DecimalPipe,
    MatIconModule
  ]
})
export class ByStockAggregateTable {
  readonly tradesData = input<readonly TradesByStockRow[]>([]);

  // Sorting state
  protected readonly sortColumn = signal<SortableColumn | null>(null);
  protected readonly sortAscending = signal(false); // Default: descending for amount

  // Computed sorted data
  protected readonly sortedData = computed(() => {
    const sortCol = this.sortColumn() || 'totalGrossAmount';
    const ascending = this.sortAscending();
    const data = [...this.tradesData()];

    if (!sortCol) {
      // Default: highest gross amount first (descending)
      return data.sort((a, b) => b.totalGrossAmount - a.totalGrossAmount);
    }

    return data.sort((a, b) => {
      let aVal: any = a[sortCol];
      let bVal: any = b[sortCol];

      // Numeric comparison (all fields are numeric or comparable)
      if (typeof aVal === 'number' && typeof bVal === 'number') {
        return ascending ? aVal - bVal : bVal - aVal;
      }

      // String comparison (symbol)
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
      // Set new column
      this.sortColumn.set(column);
      // Default to descending for monetary amounts; ascending for counts and symbol
      if (column === 'totalGrossAmount') {
        this.sortAscending.set(false);
      } else {
        this.sortAscending.set(true);
      }
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
}
