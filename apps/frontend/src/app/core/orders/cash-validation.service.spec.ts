import { TestBed } from '@angular/core/testing';
import { CashValidationService } from './cash-validation.service';

/**
 * Unit tests for mock CashValidationService
 * Tests the mock validation logic used in tests
 */
describe('CashValidationService (Mock)', () => {
  let service: CashValidationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CashValidationService],
    });
    service = TestBed.inject(CashValidationService);
  });

  /**
   * Test: Get mock balance for Account 1
   * Expected: returns 5000
   */
  it('should return mock balance for Account 1', () => {
    const balance = service.getMockBalance('1');
    expect(balance).toBe(5000.0);
  });

  /**
   * Test: Get mock balance for Account 2
   * Expected: returns 500
   */
  it('should return mock balance for Account 2', () => {
    const balance = service.getMockBalance('2');
    expect(balance).toBe(500.0);
  });

  /**
   * Test: Get mock balance for unknown account
   * Expected: returns 0
   */
  it('should return 0 balance for unknown account', () => {
    const balance = service.getMockBalance('999');
    expect(balance).toBe(0);
  });

  /**
   * Test: Account with sufficient balance
   * Account 1 has $5000, order cost is $2500
   * Expected: returns Observable with success: true
   */
  it('should validate sufficient balance for Account 1', () => {
    return service.validateCashBalance('1', 2500).toPromise().then((result) => {
      expect(result!.success).toBe(true);
      expect(result!.reason).toBeUndefined();
    });
  });

  /**
   * Test: Account with insufficient balance
   * Account 2 has $500, order cost is $1000
   * Expected: returns Observable with success: false and reason message
   */
  it('should reject insufficient balance for Account 2', () => {
    return service.validateCashBalance('2', 1000).toPromise().then((result) => {
      expect(result!.success).toBe(false);
      expect(result!.reason).toBeDefined();
      expect(result!.reason).toContain('Insufficient balance');
    });
  });

  /**
   * Test: Unknown account (not in mock data)
   * Unknown account has $0 balance
   * Expected: returns Observable with success: false
   */
  it('should reject unknown account (zero balance)', () => {
    return service.validateCashBalance('999', 100).toPromise().then((result) => {
      expect(result!.success).toBe(false);
      expect(result!.reason).toBeDefined();
    });
  });

  /**
   * Test: Exact balance match (edge case)
   * Account 1 has $5000, order cost is exactly $5000
   * Expected: returns Observable with success: true
   */
  it('should validate exact balance match', () => {
    return service.validateCashBalance('1', 5000).toPromise().then((result) => {
      expect(result!.success).toBe(true);
      expect(result!.reason).toBeUndefined();
    });
  });

  /**
   * Test: Zero order cost
   * Order cost is $0
   * Expected: returns Observable with success: true
   */
  it('should validate zero order cost', () => {
    return service.validateCashBalance('1', 0).toPromise().then((result) => {
      expect(result!.success).toBe(true);
    });
  });

  /**
   * Test: Decimal precision
   * Account 2 has $500, order cost is $250.50
   * Expected: returns Observable with success: true
   */
  it('should handle decimal precision', () => {
    return service.validateCashBalance('2', 250.5).toPromise().then((result) => {
      expect(result!.success).toBe(true);
    });
  });

  /**
   * Test: Just over balance (insufficient)
   * Account 2 has $500, order cost is $500.01
   * Expected: returns Observable with success: false
   */
  it('should reject balance just over available', () => {
    return service.validateCashBalance('2', 500.01).toPromise().then((result) => {
      expect(result!.success).toBe(false);
    });
  });
});
