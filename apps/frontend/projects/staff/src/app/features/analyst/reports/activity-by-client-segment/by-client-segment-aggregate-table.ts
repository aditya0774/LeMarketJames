import { Component, computed, input, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';

import { SegmentActivityRow } from '@staff/core/reports/reporting.service';

type SortableColumn = 'segment' | 'activeClients' | 'tradeCount' | 'totalTradedValue';

/**
 * Segment aggregate table for LMKT-43.
 * Shows one row per segment and supports client-side sorting.
 */
@Component({
  selector: 'staff-by-client-segment-aggregate-table',
  templateUrl: './by-client-segment-aggregate-table.html',
  styleUrls: ['./by-client-segment-aggregate-table.scss'],
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DecimalPipe],
})
export class ByClientSegmentAggregateTable {
  readonly reportData = input<readonly SegmentActivityRow[]>([]);

  protected readonly sortColumn = signal<SortableColumn>('segment');
  protected readonly sortAscending = signal(true);

  protected readonly sortedData = computed(() => {
    const sortCol = this.sortColumn();
    const ascending = this.sortAscending();
    const data = [...this.reportData()];

    return data.sort((a, b) => {
      const aVal = a[sortCol];
      const bVal = b[sortCol];

      if (typeof aVal === 'number' && typeof bVal === 'number') {
        return ascending ? aVal - bVal : bVal - aVal;
      }

      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return ascending ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
      }

      return 0;
    });
  });

  toggleSort(column: SortableColumn): void {
    if (this.sortColumn() === column) {
      this.sortAscending.update((val) => !val);
      return;
    }

    this.sortColumn.set(column);
    this.sortAscending.set(column !== 'totalTradedValue');
  }

  getSortIndicator(column: SortableColumn): string {
    if (this.sortColumn() !== column) {
      return '';
    }
    return this.sortAscending() ? '↑' : '↓';
  }
}
