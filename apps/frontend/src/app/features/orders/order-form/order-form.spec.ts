import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { OrderFormComponent } from './order-form';
import { OrdersService } from '../../../core/orders/orders.service';
import { CashValidationService } from '../../../core/orders/cash-validation.service';
import { BalanceResponse, OrderResponse } from '../../../shared/models/order.model';

/**
 * Mock OrdersService for testing
 * Tracks method calls and allows customization of return values
 */
class MockOrdersService {
  callCount = { getBalance: 0, createOrder: 0 };
  balanceResponse: BalanceResponse = {
    success: true,
    balance: {
      cash: 5000,
      invested: 0,
      totalValue: 5000,
      buyingPower: 5000,
      dayGainLoss: 0,
      dayGainLossPercent: 0,
      totalGainLoss: 0,
      totalGainLossPercent: 0,
      currency: 'USD',
    },
  };
  createOrderResponse: OrderResponse = { success: true, orderId: '123' };
  createOrderShouldFail = false;
  createOrderError: any = null;

  getBalance(): Promise<BalanceResponse> {
    this.callCount.getBalance++;
    return Promise.resolve(this.balanceResponse);
  }

  createOrder(request: any): Promise<OrderResponse> {
    this.callCount.createOrder++;
    if (this.createOrderShouldFail && this.createOrderError) {
      return Promise.reject(this.createOrderError);
    }
    return Promise.resolve(this.createOrderResponse);
  }

  getOrders(): Promise<any> {
    return Promise.resolve([]);
  }

  getOrder(id: string): Promise<any> {
    return Promise.resolve(null);
  }
}

describe('OrderFormComponent - Frontend Balance Validation', () => {
  let component: OrderFormComponent;
  let fixture: ComponentFixture<OrderFormComponent>;
  let ordersServiceMock: MockOrdersService;
  let cashValidationService: CashValidationService;

  beforeEach(async () => {
    ordersServiceMock = new MockOrdersService();

    await TestBed.configureTestingModule({
      imports: [
        OrderFormComponent,
        CommonModule,
        ReactiveFormsModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        MatSelectModule,
      ],
      providers: [
        { provide: OrdersService, useValue: ordersServiceMock },
        CashValidationService,
      ],
    }).compileComponents();

    cashValidationService = TestBed.inject(CashValidationService);
    fixture = TestBed.createComponent(OrderFormComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });

    it('should fetch account balance on init', async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      expect(ordersServiceMock.callCount.getBalance).toBe(1);
    });

    it('should display balance after fetching', async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      const balance = (component as any).balance();
      expect(balance).toBeTruthy();
      expect(balance?.cash).toBe(5000);
    });
  });

  describe('Required Cash Calculation', () => {
    it('should calculate required cash for BUY orders', () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      const required = (component as any).getRequiredCash();
      expect(required).toBe(1500);
    });

    it('should return 0 for SELL orders (no cash check)', () => {
      (component as any).form.patchValue({
        type: 'SELL',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      const required = (component as any).getRequiredCash();
      expect(required).toBe(0);
    });

    it('should handle empty quantity or price', () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: null,
        price: 150,
      });
      const required = (component as any).getRequiredCash();
      expect(required).toBe(0);
    });

    it('should calculate decimal amounts correctly', () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 5,
        price: 123.45,
      });
      const required = (component as any).getRequiredCash();
      expect(required).toBeCloseTo(617.25, 2);
    });
  });

  describe('Balance Validation - Submit Button', () => {
    beforeEach(() => {
      (component as any).balance.set({
        cash: 5000,
        invested: 0,
        totalValue: 5000,
        buyingPower: 5000,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
    });

    it('should disable submit when balance insufficient', () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 100,
        price: 150,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should enable submit when balance sufficient', () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(false);
    });

    it('should disable submit when form invalid', () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: '',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should enable submit for SELL orders regardless of balance', () => {
      (component as any).balance.set({
        cash: 10,
        invested: 0,
        totalValue: 10,
        buyingPower: 10,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      }); // Very low balance
      (component as any).form.patchValue({
        type: 'SELL',
        symbol: 'AAPL',
        quantity: 100,
        price: 150,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(false);
    });

    it('should disable submit with zero balance and BUY order', () => {
      (component as any).balance.set({
        cash: 0,
        invested: 0,
        totalValue: 0,
        buyingPower: 0,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 1,
        price: 10,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should enable submit with exact balance match', () => {
      (component as any).balance.set({
        cash: 1500,
        invested: 0,
        totalValue: 1500,
        buyingPower: 1500,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(false);
    });

    it('should disable submit with balance just below required', () => {
      (component as any).balance.set({
        cash: 1499.99,
        invested: 0,
        totalValue: 1499.99,
        buyingPower: 1499.99,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(true);
    });
  });

  describe('Balance Error Message Display', () => {
    it('should show error message when balance insufficient', () => {
      (component as any).balance.set({
        cash: 100,
        invested: 0,
        totalValue: 100,
        buyingPower: 100,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 100,
        price: 150,
      });
      fixture.detectChanges();
      const error = (component as any).getBalanceErrorMessage();
      expect(error).toBeTruthy();
      expect(error).toContain('Insufficient balance');
    });

    it('should hide error message when balance sufficient', () => {
      (component as any).balance.set({
        cash: 5000,
        invested: 0,
        totalValue: 5000,
        buyingPower: 5000,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      const error = (component as any).getBalanceErrorMessage();
      expect(error).toBeFalsy();
    });

    it('should not show error for SELL orders', () => {
      (component as any).balance.set({
        cash: 10,
        invested: 0,
        totalValue: 10,
        buyingPower: 10,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'SELL',
        symbol: 'AAPL',
        quantity: 100,
        price: 150,
      });
      fixture.detectChanges();
      const error = (component as any).getBalanceErrorMessage();
      expect(error).toBeFalsy();
    });

    it('should display correct required vs available amounts', () => {
      (component as any).balance.set({
        cash: 1000,
        invested: 0,
        totalValue: 1000,
        buyingPower: 1000,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      const error = (component as any).getBalanceErrorMessage();
      expect(error).toContain('1500');
      expect(error).toContain('1000');
    });
  });

  describe('Form Submission', () => {
    beforeEach(() => {
      (component as any).balance.set({
        cash: 5000,
        invested: 0,
        totalValue: 5000,
        buyingPower: 5000,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      // Reset error flags
      ordersServiceMock.createOrderShouldFail = false;
      ordersServiceMock.createOrderError = null;
    });

    it('should submit order successfully with sufficient balance', async () => {
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      await (component as any).submit();
      expect(ordersServiceMock.callCount.createOrder).toBe(1);
    });

    it('should handle API error gracefully', async () => {
      ordersServiceMock.createOrderShouldFail = true;
      ordersServiceMock.createOrderError = new Error('API error');
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      try {
        await (component as any).submit();
      } catch (e) {
        // Error expected
      }
      // Error message should be set
      expect((component as any).errorMessage()).toBeDefined();
    });

    it('should handle INSUFFICIENT_CASH error from backend', async () => {
      const error = new HttpErrorResponse({
        error: { reason: 'Insufficient balance' },
        status: 400,
      });
      ordersServiceMock.createOrderShouldFail = true;
      ordersServiceMock.createOrderError = error;
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 10,
        price: 150,
      });
      fixture.detectChanges();
      try {
        await (component as any).submit();
      } catch (e) {
        // Error expected
      }
      expect((component as any).errorMessage()).toContain('Insufficient balance');
    });

    it('should not submit when balance check fails before API call', () => {
      (component as any).balance.set({
        cash: 100,
        invested: 0,
        totalValue: 100,
        buyingPower: 100,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 100,
        price: 150,
      });
      fixture.detectChanges();
      (component as any).submit();
      // Submit should be blocked by disabled button, API should not be called
      expect(ordersServiceMock.callCount.createOrder).toBe(0);
    });
  });

  describe('Edge Cases', () => {
    it('should handle very small balances', () => {
      (component as any).balance.set({
        cash: 0.01,
        invested: 0,
        totalValue: 0.01,
        buyingPower: 0.01,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 1,
        price: 0.02,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should handle large order amounts', () => {
      (component as any).balance.set({
        cash: 1000000,
        invested: 0,
        totalValue: 1000000,
        buyingPower: 1000000,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 100000,
        price: 500,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(true); // 50M > 1M
    });

    it('should handle decimal precision in calculations', () => {
      (component as any).balance.set({
        cash: 500.50,
        invested: 0,
        totalValue: 500.50,
        buyingPower: 500.50,
        dayGainLoss: 0,
        dayGainLossPercent: 0,
        totalGainLoss: 0,
        totalGainLossPercent: 0,
        currency: 'USD',
      });
      (component as any).form.patchValue({
        type: 'BUY',
        symbol: 'AAPL',
        quantity: 2,
        price: 250.25,
      });
      fixture.detectChanges();
      expect((component as any).isSubmitDisabled()).toBe(false); // 500.50 >= 500.50
    });
  });

  describe('CashValidationService Integration', () => {
    it('should inject CashValidationService', () => {
      expect(cashValidationService).toBeTruthy();
    });

    it('should use mock balance data from CashValidationService', () => {
      const mockBalance = cashValidationService.getMockBalance('1');
      expect(mockBalance).toBe(5000);
    });

    it('should validate using service mock data', () => {
      return cashValidationService.validateCashBalance('1', 2500).toPromise().then((result) => {
        expect(result!.success).toBe(true);
      });
    });
  });
});
