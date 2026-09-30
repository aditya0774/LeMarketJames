import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { OrderResponse } from '../../../core/orders/order.service';
import { InstrumentCatalog } from '../../../core/market/instrument-catalog';
import {
  ORDER_FILTERS,
  OrderFilter,
  matchesFilter,
  statusLabel,
  statusPillClass,
} from './order-status';

const PAGE_SIZE = 5;
type Period = 'day' | 'month' | 'year';

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
  readonly loading = input(false);
  readonly error = input<string | null>(null);

  protected readonly filters = ORDER_FILTERS;
  protected readonly filter = signal<OrderFilter>('ALL');
  protected readonly page = signal(1);
  protected readonly period = signal<Period>('day');
  protected readonly dateValue = signal('');
  protected readonly dateError = computed(() => this.period() === 'year' && this.dateValue()
    && (!/^[0-9]{4}$/.test(this.dateValue()) || Number(this.dateValue()) < 1)
    ? 'Enter a four-digit year between 0001 and 9999.' : null);
  protected readonly statusPillClass = statusPillClass;
  protected readonly statusLabel = statusLabel;

  /** Newest first, then narrowed by the active chip. */
  protected readonly filtered = computed(() =>
    [...this.orders()]
      .filter((o) => matchesFilter(o.orderStatus, this.filter()))
      .sort((a, b) => Date.parse(b.submittedAt) - Date.parse(a.submittedAt)),
  );

  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));
  protected readonly pages = computed(() => Array.from({ length: this.pageCount() }, (_, i) => i + 1));

  protected readonly pageRows = computed(() => {
    const start = (this.page() - 1) * PAGE_SIZE;
    return this.filtered().slice(start, start + PAGE_SIZE);
  });

  protected readonly rangeStart = computed(() => (this.filtered().length ? (this.page() - 1) * PAGE_SIZE + 1 : 0));
  protected readonly rangeEnd = computed(() => Math.min(this.page() * PAGE_SIZE, this.filtered().length));

  setFilter(filter: OrderFilter): void {
    this.filter.set(filter);
    this.page.set(1);
  }

  setPeriod(value: string): void {
    if (value !== 'day' && value !== 'month' && value !== 'year') return;
    this.period.set(value);
    this.setDate('');
  }

  setDate(value: string): void {
    this.dateValue.set(value);
    this.page.set(1);
    // Wait for a complete year instead of requesting partial values while typing.
    if (!value || (this.period() === 'year' ? /^[0-9]{4}$/.test(value) && Number(value) > 0 : true)) {
      this.dateChange.emit(value);
    }
  }

  clearFilter(): void {
    this.setDate('');
    this.filter.set('ALL');
  }

  goToPage(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pageCount()));
  }

  // Orders only carry instrumentId until the backend adds symbol (planned in contracts/C6-api.md).
  protected symbolFor(order: OrderResponse): string {
    return this.catalog.byId(order.instrumentId)?.symbol ?? `#${order.instrumentId}`;
  }

  protected nameFor(order: OrderResponse): string {
    return this.catalog.byId(order.instrumentId)?.name ?? '';
  }
}
