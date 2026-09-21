import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { OrderResponse, OrderService } from '../../core/orders/order.service';
import { HoldingDto } from '../../shared/models/holdings.model';
import { StockSearch } from './stock-search/stock-search';
import { StatStrip } from './stat-strip/stat-strip';
import { PortfolioPanel, portfolioTotals } from './portfolio-panel/portfolio-panel';
import { OrdersPanel } from './orders-panel/orders-panel';
import { isOpenStatus } from './orders-panel/order-status';

/**
 * Dashboard Component
 *
 * Signed-in home from the LeUI mockup. Container only: it loads the account's holdings
 * and orders, derives the headline numbers, and hands everything to presentational
 * children (search, stat strip, portfolio and orders panels).
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, StockSearch, StatStrip, PortfolioPanel, OrdersPanel],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  protected readonly auth = inject(Auth);
  private readonly holdingsService = inject(HoldingsService);
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly holdings = signal<HoldingDto[]>([]);
  protected readonly holdingsLoading = signal(true);
  protected readonly holdingsError = signal<string | null>(null);

  protected readonly orders = signal<OrderResponse[]>([]);
  protected readonly ordersLoading = signal(true);
  protected readonly ordersError = signal<string | null>(null);

  /** Set when either request comes back 401, which means the JWT cookie has expired. */
  protected readonly sessionExpired = signal(false);

  protected readonly totals = computed(() => portfolioTotals(this.holdings()));
  protected readonly openOrders = computed(() => this.orders().filter((o) => isOpenStatus(o.orderStatus)).length);

  ngOnInit(): void {
    this.loadHoldings();
    this.loadOrders();
  }

  protected openTrade(symbol: string): void {
    this.router.navigate(['/trade', symbol]);
  }

  protected async logout(): Promise<void> {
    try {
      await this.auth.logout();
    } finally {
      await this.router.navigate(['/']);
    }
  }

  private loadHoldings(): void {
    try {
      this.holdingsService
        .getOwnHoldings()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response) => {
            this.holdings.set(response.holdings ?? []);
            this.holdingsLoading.set(false);
          },
          error: (err) => {
            // HoldingsService already maps errors to safe, user-facing messages.
            this.holdingsError.set(this.holdingsService.error()?.message ?? 'Unable to load holdings.');
            this.holdingsLoading.set(false);
            this.flagExpiredSession(err);
          },
        });
    } catch (err) {
      // getOwnHoldings throws synchronously when there is no account on the session.
      this.holdingsError.set((err as Error).message);
      this.holdingsLoading.set(false);
    }
  }

  private loadOrders(): void {
    const accountId = this.auth.currentAccountId();
    if (!accountId) {
      this.ordersError.set('No trading account is linked to this login.');
      this.ordersLoading.set(false);
      return;
    }
    this.orderService
      .getOrdersByAccountId(accountId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (orders) => {
          this.orders.set(orders ?? []);
          this.ordersLoading.set(false);
        },
        error: (err) => {
          this.ordersError.set('Unable to load orders. Please try again.');
          this.ordersLoading.set(false);
          this.flagExpiredSession(err);
        },
      });
  }

  private flagExpiredSession(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 401) {
      this.sessionExpired.set(true);
    }
  }
}
