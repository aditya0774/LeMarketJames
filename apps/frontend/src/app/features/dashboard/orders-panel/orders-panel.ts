import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { OrdersConnectionState, OrderResponse } from '../../../core/orders/order.service';
import { InstrumentCatalog } from '../../../core/market/instrument-catalog';
import {
  ORDER_FILTERS,
  OrderFilter,
  matchesFilter,
  statusLabel,
  statusPillClass,
} from './order-status';
import { rejectionReasonLabel } from './rejection-reason';
import { OrderHistoryRow, toOrderHistoryRow } from './order-history-row';

const PAGE_SIZE = 5;
type Period = 'day' | 'month' | 'year';

/** Year 0000 does not exist, so a valid year filter is 0001 to 9999. */
function isFourDigitYear(value: string): boolean {
  return /^\d{4}$/.test(value) && Number(value) >= 1;
}

/**
 * Orders table with status filter chips and client-side pagination. Presentational:
 * the Dashboard loads the orders and passes them in.
 */
@Component({
  selector: 'app-orders-panel',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './orders-panel.html',
  styleUrl: './orders-panel.css',
})
export class OrdersPanel {
  private readonly catalog = inject(InstrumentCatalog);

  readonly orders = input<readonly OrderResponse[]>([]);
  readonly dateChange = output<string>();
  readonly pageChange = output<number>();
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly liveConnection = input<OrdersConnectionState>('connecting');
  readonly serverPagination = input(false);
  readonly currentPage = input(1);
  readonly hasNextPage = input(false);
  readonly totalOrders = input<number | null>(null);

  protected readonly filters = ORDER_FILTERS;
  protected readonly filter = signal<OrderFilter>('ALL');
  protected readonly page = signal(1);
  protected readonly period = signal<Period>('day');
  protected readonly dateValue = signal('');
  protected readonly dateError = computed(() => this.period() === 'year' && this.dateValue()
    && !isFourDigitYear(this.dateValue())
    ? 'Enter a four-digit year between 0001 and 9999.' : null);
  protected readonly statusPillClass = statusPillClass;
  protected readonly statusLabel = statusLabel;
  protected readonly rejectionReasonLabel = rejectionReasonLabel;
  protected readonly liveConnectionLabel = computed(() => {
    if (this.liveConnection() === 'reconnecting') {
      return 'Reconnecting';
    }
    if (this.liveConnection() === 'live') {
      return 'Live';
    }
    return 'Connecting';
  });

  protected readonly rows = computed(() =>
    this.orders().map((order) => toOrderHistoryRow(order, this.catalog.byId(order.instrumentId))),
  );

  /** Newest first, then narrowed by the active chip. */
  protected readonly filtered = computed(() =>
    [...this.rows()]
      .filter((row) => matchesFilter(row.order.orderStatus, this.filter()))
      .sort((a, b) => Date.parse(b.submittedAt) - Date.parse(a.submittedAt)),
  );

  protected readonly isServerPagination = computed(() => this.serverPagination());
  protected readonly activePage = computed(() => this.isServerPagination() ? this.currentPage() : this.page());

  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));
  protected readonly pages = computed(() => Array.from({ length: this.pageCount() }, (_, i) => i + 1));

  protected readonly pageRows = computed(() => {
    if (this.isServerPagination()) {
      return this.filtered();
    }
    const start = (this.page() - 1) * PAGE_SIZE;
    return this.filtered().slice(start, start + PAGE_SIZE);
  });

  protected readonly rangeStart = computed(() => {
    if (!this.filtered().length) {
      return 0;
    }
    if (this.isServerPagination()) {
      return (this.activePage() - 1) * PAGE_SIZE + 1;
    }
    return (this.page() - 1) * PAGE_SIZE + 1;
  });
  protected readonly rangeEnd = computed(() => {
    if (this.isServerPagination()) {
      return this.rangeStart() + this.pageRows().length - 1;
    }
    return Math.min(this.page() * PAGE_SIZE, this.filtered().length);
  });
  /**
   * Null while a server-paged total is still unknown: the rows in hand are one page, not the total,
   * so the footer leaves "of N" out rather than show a wrong number.
   */
  protected readonly displayTotal = computed(() =>
    this.totalOrders() ?? (this.isServerPagination() ? null : this.filtered().length));
  protected readonly disablePrevious = computed(() => this.activePage() <= 1);
  protected readonly disableNext = computed(() => {
    if (this.isServerPagination()) {
      return !this.hasNextPage();
    }
    return this.page() >= this.pageCount();
  });

  setFilter(filter: OrderFilter): void {
    this.filter.set(filter);
    if (!this.isServerPagination()) {
      this.page.set(1);
    }
  }

  setPeriod(value: string): void {
    if (value !== 'day' && value !== 'month' && value !== 'year') {
      return;
    }
    this.period.set(value);
    this.setDate('');
  }

  setDate(value: string): void {
    this.dateValue.set(value);
    this.page.set(1);
    if (this.isServerPagination()) {
      this.pageChange.emit(1);
    }
    // Wait for a complete year instead of requesting partial values while typing.
    if (!value || (this.period() === 'year' ? /^\d{4}$/.test(value) && Number(value) > 0 : true)) {
      this.dateChange.emit(value);
    }
  }

  clearFilter(): void {
    this.setDate('');
    this.filter.set('ALL');
  }

  goToPage(page: number): void {
    if (this.isServerPagination()) {
      if (page < 1) {
        return;
      }
      this.pageChange.emit(page);
      return;
    }
    this.page.set(Math.min(Math.max(1, page), this.pageCount()));
  }

  protected fillPriceFor(row: OrderHistoryRow): number | null {
    if (row.order.orderStatus !== 'FILLED') {
      return null;
    }
    return row.order.pricePerUnit ?? null;
  }

  protected rejectionReasonFor(row: OrderHistoryRow): string | null {
    if (row.order.orderStatus !== 'REJECTED') {
      return null;
    }
    return this.rejectionReasonLabel(row.rejectionReason);
  }
}
