import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { CashMovement, CashService } from '../../../core/cash/cash.service';
import { BalanceInfo } from '../../../shared/models/order.model';

const MONEY = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** Dollars, optionally with a $ sign, thousands commas and up to two decimals. */
const AMOUNT = /^\d+(\.\d{1,2})?$/;

/**
 * Reads the amount box: the number, or an error to show, or neither while it is still empty.
 * The per-movement limit is the server's to enforce (a C5 setting); its message is shown as is.
 * Exported for unit testing.
 */
export function checkAmount(
  text: string,
  kind: CashMovement,
  buyingPower: number | null,
): { amount: number | null; error: string | null } {
  const cleaned = text.trim().replace(/^\$/, '').replace(/,/g, '');
  if (!cleaned) {
    return { amount: null, error: null };
  }
  if (!AMOUNT.test(cleaned)) {
    return { amount: null, error: 'Enter an amount in dollars and cents, like 250.00.' };
  }
  const amount = Number(cleaned);
  if (amount <= 0) {
    return { amount: null, error: 'Enter an amount above $0.' };
  }
  if (kind === 'WITHDRAW' && buyingPower !== null && amount > buyingPower) {
    return { amount: null, error: `You can withdraw up to ${MONEY.format(Math.max(0, buyingPower))}.` };
  }
  return { amount, error: null };
}

/** A fresh id for one attempt; crypto.randomUUID needs a secure context, so fall back to random bytes. */
function newRequestId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * The redesign's "Move money" dialog: deposit to or withdraw from buying power, with quick
 * amounts, the buying power before and after, and a confirmation once the money has moved.
 *
 * A modal: focus starts in the amount box, Tab stays inside, Escape closes it, and focus goes back
 * to whatever opened it.
 */
@Component({
  selector: 'app-move-money',
  imports: [CurrencyPipe],
  templateUrl: './move-money.html',
  styleUrl: './move-money.css',
  // Escape is heard document-wide: the submit button disables itself while sending, which drops
  // focus out of the dialog, and Escape must still close it.
  host: { '(document:keydown.escape)': 'onEscape($event)', '(keydown.tab)': 'keepFocusInside($event)', '(keydown.shift.tab)': 'keepFocusInside($event)' },
})
export class MoveMoney {
  private readonly cash = inject(CashService);

  /** Which tab the dialog opens on. */
  readonly start = input<CashMovement>('DEPOSIT');
  readonly buyingPower = input<number | null>(null);
  readonly closed = output<void>();
  readonly moved = output<BalanceInfo>();

  protected readonly kind = linkedSignal(() => this.start());
  protected readonly amountText = signal('');
  protected readonly pending = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly done = signal<{ kind: CashMovement; amount: number; buyingPower: number } | null>(null);

  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');
  private readonly amountBox = viewChild<ElementRef<HTMLInputElement>>('amountBox');
  /** Kept across retries of the same amount, so a resend is never applied twice. */
  private requestId = newRequestId();

  protected readonly check = computed(() => checkAmount(this.amountText(), this.kind(), this.buyingPower()));
  protected readonly after = computed(() => {
    const power = this.buyingPower();
    const amount = this.check().amount ?? 0;
    return power === null ? null : Math.max(0, power + (this.kind() === 'DEPOSIT' ? amount : -amount));
  });
  protected readonly quickAmounts = computed(() => {
    if (this.kind() === 'DEPOSIT') {
      return [500, 1000, 5000].map((value) => ({ label: MONEY.format(value).replace('.00', ''), value }));
    }
    const all = Math.max(0, this.buyingPower() ?? 0);
    return [
      ...[500, 1000, 5000].map((value) => ({ label: MONEY.format(value).replace('.00', ''), value })),
      { label: 'All', value: all },
    ];
  });
  protected readonly cta = computed(() => {
    const amount = this.check().amount;
    return `${this.kind() === 'DEPOSIT' ? 'Deposit' : 'Withdraw'}${amount ? ' ' + MONEY.format(amount) : ''}`;
  });

  constructor() {
    const opener = document.activeElement as HTMLElement | null;
    inject(DestroyRef).onDestroy(() => opener?.focus?.());
    afterNextRender(() => this.amountBox()?.nativeElement.focus());
  }

  protected choose(kind: CashMovement): void {
    if (kind !== this.kind()) {
      this.kind.set(kind);
      this.changed();
    }
  }

  protected setAmount(text: string): void {
    this.amountText.set(text);
    this.changed();
  }

  protected pick(value: number): void {
    this.setAmount(value.toFixed(2));
  }

  protected async submit(): Promise<void> {
    const amount = this.check().amount;
    if (amount === null || this.pending()) {
      return;
    }
    this.pending.set(true);
    this.serverError.set(null);
    try {
      const response = await this.cash.move(this.kind(), amount, this.requestId);
      if (!response.success || !response.balance) {
        this.serverError.set(response.error ?? 'Something went wrong. Try again in a moment.');
        return;
      }
      this.done.set({ kind: this.kind(), amount, buyingPower: response.balance.buyingPower });
      this.moved.emit(response.balance);
      this.requestId = newRequestId();
    } catch (err) {
      this.serverError.set(this.describe(err));
    } finally {
      this.pending.set(false);
    }
  }

  protected onEscape(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }

  /** A new amount or direction is a new movement, so it gets its own id. */
  private changed(): void {
    this.serverError.set(null);
    this.requestId = newRequestId();
  }

  private describe(err: unknown): string {
    if (!(err instanceof HttpErrorResponse)) {
      return 'Something went wrong. Try again in a moment.';
    }
    if (err.status === 404 || err.status === 405 || err.status === 501) {
      return "Deposits and withdrawals aren't available yet.";
    }
    if (err.status === 400 && typeof err.error?.error === 'string') {
      return err.error.error;
    }
    if (err.status === 401) {
      return 'Your session has ended. Log in again to move money.';
    }
    // Timed out or failed on the way back: the money may have moved, and a retry is safe.
    return "We couldn't confirm the transfer. Try again; it won't be applied twice.";
  }

  protected keepFocusInside(event: Event): void {
    // Never empty: the Close button is always there.
    const focusable = Array.from(
      this.panel().nativeElement.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled])'),
    );
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    // Tab past the last control wraps to the first, and Shift+Tab before the first to the last.
    const backwards = (event as KeyboardEvent).shiftKey;
    const edge = backwards ? first : last;
    if (document.activeElement === edge) {
      event.preventDefault();
      (backwards ? last : first).focus();
    }
  }
}
