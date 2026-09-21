import { Component, computed, inject, input, signal } from '@angular/core';
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

/**
 * Orders table with status filter chips and client-side pagination. Presentational:
 * the Dashboard loads the orders and passes them in.
 */
@Component({
  selector: 'app-orders-panel',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './orders-panel.html',
})
export class OrdersPanel {
  private readonly catalog = inject(InstrumentCatalog);

  readonly orders = input<readonly OrderResponse[]>([]);
  readonly loading = input(false);
  readonly error = input<string | null>(null);

  protected readonly filters = ORDER_FILTERS;
  protected readonly filter = signal<OrderFilter>('ALL');
  protected readonly page = signal(1);
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

  goToPage(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pageCount()));
  }

  // Orders only carry instrumentId until the backend adds symbol (see API-CONTRACTS.md).
  protected symbolFor(order: OrderResponse): string {
    return this.catalog.byId(order.instrumentId)?.symbol ?? `#${order.instrumentId}`;
  }

  protected nameFor(order: OrderResponse): string {
    return this.catalog.byId(order.instrumentId)?.name ?? '';
  }
}
