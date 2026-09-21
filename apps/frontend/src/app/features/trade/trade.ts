import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, map, retry, switchMap, tap } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { HoldingsService } from '../../core/holdings/holdings.service';
import { InstrumentCatalog } from '../../core/market/instrument-catalog';
import { OrderService } from '../../core/orders/order.service';
import { DEFAULT_QUOTE_REFRESH_MS, Quotes } from '../../core/quotes/quotes';
import { Quote } from '../../shared/models/quote.model';
import { Sparkline } from '../../shared/components/sparkline/sparkline';
import { CompactNumberPipe } from '../../shared/pipes/compact-number.pipe';

export type Side = 'BUY' | 'SELL';

/** How many polled prices the sparkline keeps (~2.5 minutes at the default refresh). */
const HISTORY_LENGTH = 30;

/**
 * Trade page (mockup "Stock / Trade"): a buy/sell market order form next to a live price
 * panel for one stock, addressed by /trade/:symbol.
 */
@Component({
  selector: 'app-trade',
  imports: [CurrencyPipe, DecimalPipe, RouterLink, Sparkline, CompactNumberPipe],
  templateUrl: './trade.html',
})
export class Trade {
  private readonly auth = inject(Auth);
  private readonly catalog = inject(InstrumentCatalog);
  private readonly holdingsService = inject(HoldingsService);
  private readonly orderService = inject(OrderService);

  protected readonly symbol = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((params) => (params.get('symbol') ?? '').toUpperCase())),
    { initialValue: '' },
  );
  protected readonly instrument = computed(() => this.catalog.bySymbol(this.symbol()));

  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoteError = signal(false);
  protected readonly priceHistory = signal<number[]>([]);

  protected readonly side = signal<Side>('BUY');
  protected readonly quantity = signal(1);
  protected readonly submitting = signal(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  /** Shares held per symbol; null until holdings load (or if they fail to). */
  private readonly heldBySymbol = signal<Record<string, number> | null>(null);
  protected readonly held = computed(() => this.heldBySymbol()?.[this.symbol()] ?? 0);
  protected readonly holdingsKnown = computed(() => this.heldBySymbol() !== null);

  protected readonly quantityValid = computed(() => Number.isInteger(this.quantity()) && this.quantity() >= 1);
  protected readonly exceedsHoldings = computed(
    () => this.side() === 'SELL' && this.holdingsKnown() && this.quantity() > this.held(),
  );
  protected readonly estimatedTotal = computed(() => (this.quote()?.price ?? 0) * this.quantity());
  protected readonly previousClose = computed(() => {
    const q = this.quote();
    return q ? q.price - q.priceChange : null;
  });

  protected readonly canSubmit = computed(
    () =>
      !!this.instrument()?.tradable &&
      this.quantityValid() &&
      !this.exceedsHoldings() &&
      !this.submitting(),
  );

  constructor() {
    const quotes = inject(Quotes);
    inject(ActivatedRoute)
      .paramMap.pipe(
        map((params) => (params.get('symbol') ?? '').toUpperCase()),
        tap(() => this.resetForSymbol()),
        switchMap((symbol) =>
          this.catalog.bySymbol(symbol)
            ? quotes.watchQuote(symbol).pipe(
                tap({ error: () => this.quoteError.set(true) }),
                // Keep polling through transient failures instead of freezing the panel.
                retry({ delay: DEFAULT_QUOTE_REFRESH_MS }),
              )
            : EMPTY,
        ),
        takeUntilDestroyed(),
      )
      .subscribe((response) => {
        this.quote.set(response.quote);
        this.quoteError.set(false);
        this.priceHistory.update((h) => [...h, response.quote.price].slice(-HISTORY_LENGTH));
      });

    this.loadHoldings();
  }

  protected setSide(side: Side): void {
    this.side.set(side);
    this.clearMessages();
  }

  protected step(delta: number): void {
    const current = Number.isFinite(this.quantity()) ? Math.floor(this.quantity()) : 0;
    this.quantity.set(Math.max(1, current + delta));
  }

  protected setQuantity(raw: string): void {
    this.quantity.set(raw === '' ? 0 : Number(raw));
  }

  submit(): void {
    const instrument = this.instrument();
    const accountId = this.auth.currentAccountId();
    if (!this.canSubmit() || !instrument) {
      return;
    }
    if (!accountId) {
      this.errorMessage.set('No trading account is linked to this login.');
      return;
    }

    this.clearMessages();
    this.submitting.set(true);
    const side = this.side();
    const quantity = this.quantity();

    this.orderService.createOrder({ accountId, instrumentId: instrument.instrumentId, orderType: side, quantity }).subscribe({
      next: (order) => {
        this.submitting.set(false);
        if (order.orderStatus === 'REJECTED') {
          this.errorMessage.set(order.rejectionReason || 'The order was rejected.');
          return;
        }
        this.successMessage.set(
          `${side === 'BUY' ? 'Buy' : 'Sell'} order #${order.orderId} for ${quantity} ${instrument.symbol} ` +
            `is ${order.orderStatus.toLowerCase()}.`,
        );
        this.loadHoldings();
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(this.describeError(err));
      },
    });
  }

  private resetForSymbol(): void {
    this.quote.set(null);
    this.quoteError.set(false);
    this.priceHistory.set([]);
    this.quantity.set(1);
    this.clearMessages();
  }

  private clearMessages(): void {
    this.successMessage.set(null);
    this.errorMessage.set(null);
  }

  private loadHoldings(): void {
    try {
      this.holdingsService.getOwnHoldings().subscribe({
        next: (response) =>
          this.heldBySymbol.set(Object.fromEntries((response.holdings ?? []).map((h) => [h.symbol, h.quantity]))),
        // Leave holdings unknown; the backend still enforces the sell check.
        error: () => this.heldBySymbol.set(null),
      });
    } catch {
      this.heldBySymbol.set(null);
    }
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 401) {
        return 'Your session has expired. Please log in again.';
      }
      const body = err.error;
      return body?.reason || body?.rejectionReason || body?.error || body?.message || 'Unable to place the order.';
    }
    return err instanceof Error ? err.message : 'Unable to place the order.';
  }
}
