import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { AccountOrders } from '../../core/orders/account-orders';
import { OrderResponse, OrderService } from '../../core/orders/order.service';
import { OrdersPanel } from './orders-panel/orders-panel';
import { SessionExpired } from '../../shared/components/session-expired/session-expired';

/**
 * The Orders tab: the account's order history, newest first, five per page from the server, with
 * the date filter and live status updates.
 *
 * The total in "Showing 1–5 of N" comes from the full order list (AccountOrders) when no date is
 * chosen, and is worked out from the pages seen so far when one is.
 */
@Component({
  selector: 'app-orders-page',
  imports: [OrdersPanel, SessionExpired],
  providers: [AccountOrders],
  template: `
    @if (sessionExpired()) {
      <app-session-expired />
    }
    <app-orders-panel
      [orders]="historyOrders()"
      (dateChange)="onDateChange($event)"
      (pageChange)="onPageChange($event)"
      [loading]="loading()"
      [error]="error()"
      [liveConnection]="accountOrders.connection()"
      [serverPagination]="true"
      [currentPage]="page()"
      [hasNextPage]="hasNext()"
      [totalOrders]="total()"
    />
  `,
})
export class OrdersPage implements OnInit {
  private static readonly PAGE_SIZE = 5;

  private readonly auth = inject(Auth);
  private readonly orderService = inject(OrderService);
  private readonly catalog = inject(InstrumentCatalog);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly accountOrders = inject(AccountOrders);

  protected readonly historyOrders = signal<OrderResponse[]>([]);
  protected readonly page = signal(1);
  protected readonly hasNext = signal(false);
  private readonly filteredTotal = signal<number | null>(null);
  private readonly historyLoading = signal(true);
  private readonly historyError = signal<string | null>(null);
  private readonly ownSessionExpired = signal(false);
  private readonly date = signal('');
  private historyRequest?: Subscription;

  protected readonly loading = computed(() => !this.accountOrders.error() && this.historyLoading());
  protected readonly error = computed(() => this.accountOrders.error() ?? this.historyError());
  protected readonly sessionExpired = computed(() => this.ownSessionExpired() || this.accountOrders.sessionExpired());
  /** Unknown (null) rather than 0 while the full list is still on its way. */
  protected readonly total = computed(() => {
    if (this.date()) {
      return this.filteredTotal();
    }
    return this.accountOrders.loaded() ? this.accountOrders.orders().length : null;
  });

  ngOnInit(): void {
    // The table names each stock from the catalog; a failure only leaves names blank.
    this.catalog.load().catch((err) => this.flagExpiredSession(err));
    this.accountOrders.start();
    // A status change can move an order between pages or filters, so refetch the visible page.
    this.accountOrders.statusChanged.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadHistory());
    this.loadHistory();
  }

  protected onDateChange(date: string): void {
    this.date.set(date);
    this.page.set(1);
    this.loadHistory();
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
    this.loadHistory();
  }

  private loadHistory(): void {
    const accountId = this.auth.currentAccountId();
    if (!accountId) {
      return;
    }
    const filter = this.date()
      ? { date: this.date(), timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone }
      : undefined;

    // A newer request (another date or page) replaces any still in flight.
    this.historyRequest?.unsubscribe();
    this.historyLoading.set(true);
    this.historyError.set(null);
    const pageIndex = this.page() - 1;
    const size = OrdersPage.PAGE_SIZE;
    // One extra row tells us whether a next page exists without a separate count request.
    this.historyRequest = this.orderService
      .getOrdersByAccountIdPage(accountId, { page: pageIndex, size: size + 1 }, filter)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (orders) => {
          const all = orders ?? [];
          const hasNext = all.length > size;
          const pageOrders = all.slice(0, size);
          this.historyOrders.set(pageOrders);
          this.hasNext.set(hasNext);
          const seen = pageIndex * size + pageOrders.length;
          this.filteredTotal.set(hasNext ? seen + 1 : seen);
          this.historyLoading.set(false);
        },
        error: (err) => {
          this.historyOrders.set([]);
          this.hasNext.set(false);
          this.filteredTotal.set(0);
          this.historyError.set('Unable to load orders. Please try again.');
          this.historyLoading.set(false);
          this.flagExpiredSession(err);
        },
      });
  }

  private flagExpiredSession(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      this.ownSessionExpired.set(true);
      this.accountOrders.flagExpiredSession(err);
    }
  }
}
