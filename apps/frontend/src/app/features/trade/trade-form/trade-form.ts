import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Auth } from '../../../core/auth/auth';
import { Instrument } from '../../../core/market/instrument-catalog';
import { OrderResponse, OrderService } from '../../../core/orders/order.service';
import { Quote } from '../../../shared/models/quote.model';

export type Side = 'BUY' | 'SELL';

/** The order path shown after sending, in the order a market order moves along it. */
const SENT_STEPS: readonly { status: OrderResponse['orderStatus']; label: string }[] = [
  { status: 'SUBMITTED', label: 'Submitted' },
  { status: 'ACCEPTED', label: 'Accepted' },
  { status: 'PENDING', label: 'Pending' },
  { status: 'FILLED', label: 'Filled' },
];

/**
 * The order ticket from the redesign: Buy/Sell, shares, the (market-only) order type, the
 * estimated total and buying power after, then the confirmation once the order is sent.
 *
 * The page around it supplies the stock, its live quote, how many shares the user holds and the
 * buying power, so the same ticket serves the Overview's quick trade and the trade page; this
 * component only validates and places the order. It emits `orderPlaced` after the backend
 * accepts an order so the page can refresh holdings, balance and orders.
 */
@Component({
  selector: 'app-trade-form',
  imports: [CurrencyPipe, DecimalPipe, RouterLink],
  templateUrl: './trade-form.html',
  styleUrl: './trade-form.css',
})
export class TradeForm {
  private readonly auth = inject(Auth);
  private readonly orderService = inject(OrderService);

  /** The stock to trade; undefined when the symbol isn't in the catalog. */
  readonly instrument = input.required<Instrument | undefined>();
  readonly quote = input<Quote | null>(null);
  /** True while the quote feed is failing: no order may be priced, so the button is disabled. */
  readonly quoteError = input(false);
  /** Shares held of this stock; null when holdings couldn't be loaded. */
  readonly held = input<number | null>(null);
  /** Cash available to buy with; null when the balance couldn't be loaded. */
  readonly buyingPower = input<number | null>(null);
  readonly orderPlaced = output<void>();

  protected readonly sentSteps = SENT_STEPS;
  protected readonly side = signal<Side>('BUY');
  protected readonly quantity = signal(1);
  protected readonly submitting = signal(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly sentStatus = signal<OrderResponse['orderStatus'] | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly symbol = computed(() => this.instrument()?.symbol ?? '');
  protected readonly quantityValid = computed(() => Number.isInteger(this.quantity()) && this.quantity() >= 1);
  protected readonly exceedsHoldings = computed(() => {
    const held = this.held();
    return this.side() === 'SELL' && held !== null && this.quantity() > held;
  });
  protected readonly price = computed(() => this.quote()?.price ?? null);
  protected readonly estimatedTotal = computed(() => (this.price() ?? 0) * this.quantity());
  protected readonly powerAfter = computed(() => {
    const power = this.buyingPower();
    if (power === null || this.price() === null || !this.quantityValid()) {
      return null;
    }
    return this.side() === 'BUY' ? power - this.estimatedTotal() : power + this.estimatedTotal();
  });
  protected readonly canSubmit = computed(() => {
    const orderValid = this.quantityValid() && !this.exceedsHoldings();
    return !!this.instrument()?.tradable && orderValid && !this.submitting() && !this.quoteError();
  });
  /** How many of the four steps the sent order has reached (Filled lights all four). */
  protected readonly sentStep = computed(() => {
    const status = this.sentStatus();
    const index = SENT_STEPS.findIndex((s) => s.status === status);
    return index < 0 ? 1 : index + 1;
  });

  constructor() {
    // A different stock starts a fresh ticket.
    effect(() => {
      this.symbol();
      untracked(() => this.newTrade());
    });
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

  /** "New trade": back to one share, same stock and side. */
  newTrade(): void {
    this.quantity.set(1);
    this.clearMessages();
  }

  submit(): void {
    const instrument = this.instrument();
    const quote = this.quote();
    const accountId = this.auth.currentAccountId();
    if (!this.canSubmit() || !instrument) {
      return;
    }
    if (!accountId) {
      this.errorMessage.set('No trading account is linked to this login.');
      return;
    }

    this.clearMessages();
    const side = this.side();
    const quantity = this.quantity();

    // A buy is placed at the live price, so it can't be sent without one.
    if (side === 'BUY' && (!quote || quote.price <= 0)) {
      this.errorMessage.set('Live pricing is unavailable. Please try again in a moment.');
      return;
    }
    this.submitting.set(true);

    const order$ =
      side === 'BUY'
        ? this.orderService.submitBuyOrder({ accountId, instrumentId: instrument.instrumentId, quantity, pricePerUnit: quote!.price })
        : this.orderService.createOrder({ accountId, instrumentId: instrument.instrumentId, orderType: side, quantity });

    order$.subscribe({
      next: (order) => {
        this.submitting.set(false);
        if (!order.success || order.orderStatus === 'REJECTED') {
          this.errorMessage.set(order.reason || order.rejectionReason || 'The order was rejected.');
          return;
        }
        this.sentStatus.set(order.orderStatus);
        this.successMessage.set(
          `${side === 'BUY' ? 'Buy' : 'Sell'} order #${order.orderId} for ${quantity} ${instrument.symbol} ` +
            `is ${order.orderStatus.toLowerCase()}.`,
        );
        this.orderPlaced.emit();
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(this.describeError(err));
      },
    });
  }

  private clearMessages(): void {
    this.successMessage.set(null);
    this.sentStatus.set(null);
    this.errorMessage.set(null);
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 401) {
        return 'Your session has expired. Please log in again.';
      }
      const body = err.error;
      // Services name the refusal differently; show the first one the response carries.
      const reason = [body?.reason, body?.rejectionReason, body?.error, body?.message].find((text) => !!text);
      return reason || 'Unable to place the order.';
    }
    return err instanceof Error ? err.message : 'Unable to place the order.';
  }
}
