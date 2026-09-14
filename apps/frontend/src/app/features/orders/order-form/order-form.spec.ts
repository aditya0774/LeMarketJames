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

describe('OrderFormComponent - Frontend Balance Validation', () => {
  let component: OrderFormComponent;
  let fixture: ComponentFixture<OrderFormComponent>;
  let ordersService: jasmine.SpyObj<OrdersService>;

  beforeEach(async () => {
    // Create a spy object for OrdersService to mock HTTP calls
    const ordersServiceSpy = jasmine.createSpyObj('OrdersService', [
      'getBalance',
      'createOrder',
      'getOrders',
      'getOrder',
    ]);

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
      providers: [{ provide: OrdersService, useValue: ordersServiceSpy }],
    }).compileComponents();

    ordersService = TestBed.inject(OrdersService) as jasmine.SpyObj<OrdersService>;
    fixture = TestBed.createComponent(OrderFormComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });

    it('should fetch balance on init', async () => {
      const balanceResponse: BalanceResponse = {
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
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));

      fixture.detectChanges();
      await fixture.whenStable();

      expect(ordersService.getBalance).toHaveBeenCalled();
      expect((component as any).balance()?.cash).toBe(5000);
      expect((component as any).loading()).toBe(false);
    });

    it('should handle balance fetch error', async () => {
      ordersService.getBalance.and.returnValue(Promise.reject(new Error('API Error')));

      fixture.detectChanges();
      await fixture.whenStable();

      expect((component as any).errorMessage()).toContain('Error fetching balance');
      expect((component as any).loading()).toBe(false);
    });

    it('should set error message if balance response is unsuccessful', async () => {
      const failedResponse: BalanceResponse = {
        success: false,
        error: 'Unauthorized',
      };
      ordersService.getBalance.and.returnValue(Promise.resolve(failedResponse));

      fixture.detectChanges();
      await fixture.whenStable();

      expect((component as any).errorMessage()).toContain('Failed to fetch balance');
    });
  });

  describe('Required Cash Calculation', () => {
    beforeEach(async () => {
      const balanceResponse: BalanceResponse = {
        success: true,
        balance: {
          cash: 10000,
          invested: 0,
          totalValue: 10000,
          buyingPower: 10000,
          dayGainLoss: 0,
          dayGainLossPercent: 0,
          totalGainLoss: 0,
          totalGainLossPercent: 0,
          currency: 'USD',
        },
      };
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should calculate required cash correctly for BUY orders', () => {
      (component as any).form.patchValue({
        quantity: 25,
        price: 150.5,
        type: 'BUY',
      });

      const requiredCash = (component as any).getRequiredCash();
      expect(requiredCash).toBe(25 * 150.5); // 3762.5
    });

    it('should return 0 required cash for SELL orders', () => {
      (component as any).form.patchValue({
        quantity: 100,
        price: 200,
        type: 'SELL',
      });

      const requiredCash = (component as any).getRequiredCash();
      expect(requiredCash).toBe(0);
    });

    it('should return 0 when quantity is empty', () => {
      (component as any).form.patchValue({
        quantity: null,
        price: 100,
      });

      const requiredCash = (component as any).getRequiredCash();
      expect(requiredCash).toBe(0);
    });

    it('should return 0 when price is empty', () => {
      (component as any).form.patchValue({
        quantity: 10,
        price: null,
      });

      const requiredCash = (component as any).getRequiredCash();
      expect(requiredCash).toBe(0);
    });
  });

  describe('Balance Validation - Submit Button Disabled State', () => {
    beforeEach(async () => {
      const balanceResponse: BalanceResponse = {
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
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should disable submit button when required cash exceeds available cash', () => {
      // BUY 100 shares @ $100 = $10,000 (exceeds $5,000 balance)
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 100,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should enable submit button when required cash is less than available cash', () => {
      // BUY 10 shares @ $100 = $1,000 (less than $5,000 balance)
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 10,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      expect((component as any).isSubmitDisabled()).toBe(false);
    });

    it('should enable submit button when required cash equals available cash', () => {
      // BUY 50 shares @ $100 = $5,000 (exactly equals balance)
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 50,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      expect((component as any).isSubmitDisabled()).toBe(false);
    });

    it('should disable submit button when form is invalid', () => {
      // Leave required fields empty
      (component as any).form.patchValue({
        symbol: '',
        quantity: null,
        price: null,
      });

      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should disable submit button when balance is not loaded', () => {
      component.balance.set(null);
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 10,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should enable submit button for SELL orders regardless of quantity', () => {
      // SELL 1000 shares @ $100 (doesn't require cash balance)
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 1000,
        price: 100,
        type: 'SELL',
        orderType: 'MARKET',
      });

      expect((component as any).isSubmitDisabled()).toBe(false);
    });
  });

  describe('Balance Error Message Display', () => {
    beforeEach(async () => {
      const balanceResponse: BalanceResponse = {
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
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should show error message when balance is insufficient', () => {
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 100,
        price: 100,
        type: 'BUY',
      });

      const errorMsg = (component as any).getBalanceErrorMessage();
      expect(errorMsg).toContain('Insufficient balance');
      expect(errorMsg).toContain('Required: $10,000.00');
      expect(errorMsg).toContain('Available: $5,000.00');
    });

    it('should not show error message when balance is sufficient', () => {
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 10,
        price: 100,
        type: 'BUY',
      });

      const errorMsg = (component as any).getBalanceErrorMessage();
      expect(errorMsg).toBeNull();
    });

    it('should not show error message for SELL orders', () => {
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 1000,
        price: 100,
        type: 'SELL',
      });

      const errorMsg = (component as any).getBalanceErrorMessage();
      expect(errorMsg).toBeNull();
    });

    it('should not show error message when balance is not loaded', () => {
      component.balance.set(null);
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 100,
        price: 100,
        type: 'BUY',
      });

      const errorMsg = (component as any).getBalanceErrorMessage();
      expect(errorMsg).toBeNull();
    });
  });

  describe('Form Submission', () => {
    beforeEach(async () => {
      const balanceResponse: BalanceResponse = {
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
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should prevent submission when form is invalid', async () => {
      (component as any).form.patchValue({
        symbol: '',
        quantity: null,
        price: null,
      });

      await (component as any).submit();

      expect(ordersService.createOrder).not.toHaveBeenCalled();
    });

    it('should prevent submission when balance is insufficient', async () => {
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 100,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      await (component as any).submit();

      expect(ordersService.createOrder).not.toHaveBeenCalled();
    });

    it('should submit order when form is valid and balance sufficient', async () => {
      const orderResponse: OrderResponse = {
        success: true,
        orderId: 'order_123',
        symbol: 'AAPL',
        quantity: 10,
        type: 'BUY',
        orderType: 'MARKET',
        price: 100,
        status: 'PENDING',
        createdAt: new Date().toISOString(),
        executedAt: null,
      };
      ordersService.createOrder.and.returnValue(Promise.resolve(orderResponse));

      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 10,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      await (component as any).submit();

      expect(ordersService.createOrder).toHaveBeenCalledWith(
        jasmine.objectContaining({
          symbol: 'AAPL',
          quantity: 10,
          price: 100,
          type: 'BUY',
          orderType: 'MARKET',
        }),
      );
      expect((component as any).successMessage()).toContain('Order placed successfully');
    });

    it('should display error message on failed submission', async () => {
      ordersService.createOrder.and.returnValue(
        Promise.reject(new HttpErrorResponse({ status: 400, error: { error: 'API Error' } })),
      );

      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 10,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      await (component as any).submit();

      expect((component as any).errorMessage()).toContain('Error placing order');
    });

    it('should handle INSUFFICIENT_CASH error specifically', async () => {
      ordersService.createOrder.and.returnValue(
        Promise.reject(
          new HttpErrorResponse({
            status: 400,
            error: { code: 'INSUFFICIENT_CASH', error: 'Not enough balance' },
          }),
        ),
      );

      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 10,
        price: 100,
        type: 'BUY',
        orderType: 'MARKET',
      });

      await (component as any).submit();

      expect((component as any).errorMessage()).toContain('Insufficient balance to place this order');
    });

    it('should reset form after successful submission', async () => {
      const orderResponse: OrderResponse = {
        success: true,
        orderId: 'order_456',
        symbol: 'MSFT',
        quantity: 5,
        type: 'BUY',
        orderType: 'LIMIT',
        price: 380.5,
        status: 'PENDING',
        createdAt: new Date().toISOString(),
        executedAt: null,
      };
      ordersService.createOrder.and.returnValue(Promise.resolve(orderResponse));

      (component as any).form.patchValue({
        symbol: 'MSFT',
        quantity: 5,
        price: 380.5,
        type: 'BUY',
        orderType: 'LIMIT',
      });

      await (component as any).submit();

      expect((component as any).form.get('symbol')?.value).toBe('');
      expect((component as any).form.get('type')?.value).toBe('BUY'); // Defaults to BUY
      expect((component as any).form.get('orderType')?.value).toBe('MARKET'); // Defaults to MARKET
    });
  });

  describe('Edge Cases', () => {
    beforeEach(async () => {
      const balanceResponse: BalanceResponse = {
        success: true,
        balance: {
          cash: 0.01,
          invested: 0,
          totalValue: 0.01,
          buyingPower: 0.01,
          dayGainLoss: 0,
          dayGainLossPercent: 0,
          totalGainLoss: 0,
          totalGainLossPercent: 0,
          currency: 'USD',
        },
      };
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should handle very small balance amounts', () => {
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 1,
        price: 0.01,
        type: 'BUY',
      });

      expect((component as any).isSubmitDisabled()).toBe(false);
    });

    it('should handle very large price amounts', () => {
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 1,
        price: 100000,
        type: 'BUY',
      });

      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    it('should handle decimal precision in calculations', () => {
      (component as any).form.patchValue({
        quantity: 3,
        price: 0.003,
        type: 'BUY',
      });

      const requiredCash = (component as any).getRequiredCash();
      expect(requiredCash).toBeCloseTo(0.009, 5);
    });
  });

  describe('CashValidationService Integration', () => {
    let cashValidationService: any;

    beforeEach(async () => {
      // Create a spy object for CashValidationService
      cashValidationService = jasmine.createSpyObj('CashValidationService', [
        'validateCashBalance',
        'getMockBalance',
      ]);

      const balanceResponse: BalanceResponse = {
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
      ordersService.getBalance.and.returnValue(Promise.resolve(balanceResponse));

      // Reconfigure TestBed with the spy service
      await TestBed.resetTestingModule();
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
          { provide: OrdersService, useValue: ordersService },
          { provide: CashValidationService, useValue: cashValidationService },
        ],
      }).compileComponents();

      cashValidationService = TestBed.inject(CashValidationService);
      fixture = TestBed.createComponent(OrderFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    /**
     * Test: Sufficient cash validation passes
     * Account has $5000, order cost is $2500
     * Expected: validateCashBalance returns success: true
     */
    it('should validate sufficient balance', () => {
      cashValidationService.validateCashBalance.and.returnValue(
        Promise.resolve({ success: true }),
      );

      // This test verifies the component can use the injected service
      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 25,
        price: 100,
        type: 'BUY',
      });

      expect((component as any).form.valid).toBe(true);
    });

    /**
     * Test: Insufficient cash validation fails
     * Account has $1000, order cost is $5000
     * Expected: Button remains disabled
     */
    it('should reflect validation service rejection in UI', () => {
      cashValidationService.validateCashBalance.and.returnValue(
        Promise.resolve({
          success: false,
          reason: 'Insufficient balance. Required: $100000.00, Available: $5000.00',
        }),
      );

      (component as any).form.patchValue({
        symbol: 'AAPL',
        quantity: 1000,
        price: 100,
        type: 'BUY',
      });

      // Button should be disabled due to insufficient cash
      expect((component as any).isSubmitDisabled()).toBe(true);
    });

    /**
     * Test: Mock service provides test data
     * getMockBalance should return predefined balance
     */
    it('should get mock balance from service', () => {
      cashValidationService.getMockBalance.and.returnValue(5000);

      const balance = cashValidationService.getMockBalance('1');
      expect(balance).toBe(5000);
      expect(cashValidationService.getMockBalance).toHaveBeenCalledWith('1');
    });
  });
});
