import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';

/**
 * Mock CashValidationService
 *
 * Provides mock cash validation for testing the order form without backend calls.
 * Simulates hardcoded test accounts with predefined cash balances.
 *
 * Test data:
 * - Account ID 1: $5000.00 available cash
 * - Account ID 2: $500.00 available cash
 * - Any other account: $0 (insufficient for any order)
 */
@Injectable({ providedIn: 'root' })
export class CashValidationService {
  /**
   * Mock account balances for testing
   */
  private mockBalances: Record<string, number> = {
    '1': 5000.00,
    '2': 500.00,
  };

  /**
   * Validates that an account has sufficient cash for an order.
   * Returns immediately (no HTTP delay).
   *
   * @param accountId the account to validate
   * @param orderCost the total cost of the order (quantity * pricePerUnit)
   * @returns Observable with validation result { success: boolean, reason?: string }
   */
  validateCashBalance(accountId: string, orderCost: number): Observable<{ success: boolean; reason?: string }> {
    const balance = this.mockBalances[accountId] ?? 0;

    if (balance >= orderCost) {
      return of({ success: true });
    } else {
      return of({
        success: false,
        reason: `Insufficient balance. Required: $${orderCost.toFixed(2)}, Available: $${balance.toFixed(2)}`,
      });
    }
  }

  /**
   * Gets the mock balance for an account.
   * Used for display purposes (show available cash in the form).
   *
   * @param accountId the account to query
   * @returns the mock cash balance
   */
  getMockBalance(accountId: string): number {
    return this.mockBalances[accountId] ?? 0;
  }
}
