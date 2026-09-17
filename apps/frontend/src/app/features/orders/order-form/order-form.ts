import { Component, OnInit, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { OrdersService } from '../../../core/orders/orders.service';
import { CashValidationService } from '../../../core/orders/cash-validation.service';
import { BalanceInfo, OrderRequest, OrderType, OrderTypeValue } from '../../../shared/models/order.model';

@Component({
  selector: 'app-order-form',
  templateUrl: './order-form.html',
  styleUrls: ['./order-form.css'],
  imports: [
    ReactiveFormsModule,
    CommonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
  ],
})
/**
 * OrderForm Component
 *
 * Displays an order placement form with real-time balance validation.
 * - Fetches user's available cash on init
 * - Displays balance and disabled submit button if insufficient funds
 * - Validates form fields (quantity > 0, price > 0, etc.)
 * - Calculates required cash and blocks submit if requiredCash > availableCash
 * - Sends order to backend on submit
 * - Shows success/error feedback
 */
export class OrderFormComponent implements OnInit {
  protected readonly form: ReturnType<FormBuilder['group']>;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly loading = signal(true);
  protected readonly balance = signal<BalanceInfo | null>(null);

  // Readable order type/orderType options for the form selects
  protected readonly orderTypeOptions: OrderType[] = ['BUY', 'SELL'];
  protected readonly orderTypeValueOptions: OrderTypeValue[] = ['MARKET', 'LIMIT'];

  constructor(
    private readonly fb: FormBuilder,
    private readonly ordersService: OrdersService,
    private readonly cashValidationService: CashValidationService,
  ) {
    this.form = this.fb.group({
      symbol: ['', [Validators.required, Validators.minLength(1)]],
      quantity: [null, [Validators.required, Validators.min(1)]],
      type: ['BUY', Validators.required],
      orderType: ['MARKET', Validators.required],
      price: [null, [Validators.required, Validators.min(0.01)]],
    });
  }

  async ngOnInit(): Promise<void> {
    try {
      const response = await this.ordersService.getBalance();
      if (response.success && response.balance) {
        this.balance.set(response.balance);
      } else {
        this.errorMessage.set('Failed to fetch balance');
      }
    } catch (error) {
      this.errorMessage.set('Error fetching balance. Please try again.');
      console.error('Balance fetch error:', error);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Calculates required cash to place the order: quantity * price
   * Used to determine if submit button should be enabled.
   */
  protected getRequiredCash(): number {
    const quantity = this.form.get('quantity')?.value;
    const price = this.form.get('price')?.value;
    const type = this.form.get('type')?.value as OrderType;

    if (!quantity || !price) return 0;
    if (type === 'SELL') return 0; // SELL orders don't require cash balance

    return quantity * price;
  }

  /**
   * Determines if the submit button should be enabled.
   * Disabled if:
   * - Form is invalid
   * - Balance not loaded
   * - Required cash > available cash (for BUY orders)
   */
  protected isSubmitDisabled(): boolean {
    if (this.form.invalid || !this.balance()) {
      return true;
    }

    const type = this.form.get('type')?.value as OrderType;
    if (type === 'SELL') {
      return false; // SELL orders don't require balance check (would check holdings instead)
    }

    const requiredCash = this.getRequiredCash();
    const availableCash = this.balance()?.cash || 0;
    return requiredCash > availableCash;
  }

  /**
   * Returns an error message if balance is insufficient.
   */
  protected getBalanceErrorMessage(): string | null {
    if (!this.balance()) return null;

    const type = this.form.get('type')?.value as OrderType;
    if (type === 'SELL') return null;

    const requiredCash = this.getRequiredCash();
    const availableCash = this.balance()?.cash || 0;

    if (requiredCash > availableCash) {
      return `Insufficient balance. Required: $${requiredCash.toFixed(2)}, Available: $${availableCash.toFixed(2)}`;
    }
    return null;
  }

  /**
   * Handles form submission.
   * Validates form, checks balance one more time, then sends order to backend.
   */
  async submit(): Promise<void> {
    if (this.form.invalid || this.isSubmitDisabled()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.submitting.set(true);

    try {
      const orderRequest: OrderRequest = this.form.getRawValue() as OrderRequest;
      const response = await this.ordersService.createOrder(orderRequest);

      if (response.success && response.orderId) {
        this.successMessage.set(`Order placed successfully! Order ID: ${response.orderId}`);
        this.form.reset({ type: 'BUY', orderType: 'MARKET' });
      } else {
        this.errorMessage.set(response.error || 'Failed to place order');
      }
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        if (error.error?.code === 'INSUFFICIENT_CASH') {
          this.errorMessage.set('Insufficient balance to place this order');
        } else {
          this.errorMessage.set(error.error?.reason || error.error?.error || 'Error placing order');
        }
      } else {
        this.errorMessage.set('Error placing order. Please try again.');
      }
      console.error('Order submission error:', error);
    } finally {
      this.submitting.set(false);
    }
  }
}
