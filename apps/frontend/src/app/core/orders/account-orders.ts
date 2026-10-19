import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { Auth } from '../auth/auth';
import { OrderResponse, OrderService, OrdersConnectionState } from './order.service';
import { isOpenStatus } from './order-status';

/**
 * The signed-in account's full order list, kept current by the live order-status stream.
 *
 * Provided per page (`providers: [AccountOrders]`) rather than app-wide, so the stream is open
 * only while a page that needs it is showing and closes with the page. The Overview uses it for
 * the open-order count, the Orders page for the history total and live updates.
 *
 * Pages listen to {@link statusChanged} to reload whatever an order's progress affects
 * (holdings, balance, the visible history page).
 */
@Injectable()
export class AccountOrders {
  private readonly auth = inject(Auth);
  private readonly orderService = inject(OrderService);
  private readonly destroyRef = inject(DestroyRef);

  readonly orders = signal<OrderResponse[]>([]);
  /** False until the first full list arrives, so a count of 0 is never shown too early. */
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  readonly connection = signal<OrdersConnectionState>('connecting');
  /** Set when the server answers 401: the session cookie has expired. */
  readonly sessionExpired = signal(false);
  readonly openCount = computed(() => this.orders().filter((o) => isOpenStatus(o.orderStatus)).length);

  /** Fires when an order changed status, from the stream or seen in a reloaded list. */
  readonly statusChanged = new Subject<void>();

  private ordersRequest?: Subscription;
  private pendingRefresh = false;
  private streamRequest?: Subscription;

  /** Loads the list and opens the live stream. A login without an account gets neither. */
  start(): void {
    // Another request on the page may already have found the session expired.
    if (this.sessionExpired()) {
      return;
    }
    if (!this.auth.currentAccountId()) {
      this.error.set('No trading account is linked to this login.');
      return;
    }
    this.reload();
    // The list request can fail with 401 straight away; then there is no session to stream for.
    if (!this.sessionExpired()) {
      this.watchStream();
    }
  }

  /**
   * Fetches the full list again. A request already in flight may predate the change that
   * triggered this call, so instead of starting a second one, it queues exactly one more.
   */
  reload(): void {
    const accountId = this.auth.currentAccountId();
    if (!accountId) {
      return;
    }
    if (this.ordersRequest && !this.ordersRequest.closed) {
      this.pendingRefresh = true;
      return;
    }
    this.ordersRequest = this.orderService
      .getAllOrdersByAccountId(accountId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (orders) => {
          const previous = new Map(this.orders().map((order) => [order.orderId, order.orderStatus]));
          const list = orders ?? [];
          this.orders.set(list);
          this.loaded.set(true);
          this.error.set(null);
          if (list.some((order) => previous.has(order.orderId) && previous.get(order.orderId) !== order.orderStatus)) {
            this.statusChanged.next();
          }
          this.runPendingRefresh();
        },
        error: (err) => {
          this.flagExpiredSession(err);
          this.runPendingRefresh();
        },
      });
  }

  /** Remembers a 401 and closes the stream; other errors are left to the caller. */
  flagExpiredSession(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      this.sessionExpired.set(true);
      this.streamRequest?.unsubscribe();
    }
  }

  private runPendingRefresh(): void {
    if (this.pendingRefresh) {
      this.pendingRefresh = false;
      this.reload();
    }
  }

  private watchStream(): void {
    if (this.streamRequest && !this.streamRequest.closed) {
      return;
    }
    this.streamRequest = this.orderService
      .watchOrderStatusStream()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.connection.set(snapshot.connection);
          if (snapshot.event) {
            this.reload();
            this.statusChanged.next();
          }
        },
        error: (err) => this.flagExpiredSession(err),
      });
  }
}
