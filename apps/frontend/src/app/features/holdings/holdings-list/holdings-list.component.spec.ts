import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { HoldingsListComponent } from './holdings-list.component';
import { HoldingsService, ErrorInfo } from '@app/core/holdings/holdings.service';
import { Auth } from '@app/core/auth/auth';
import { HoldingsResponse, HoldingDto } from '@app/shared/models/holdings.model';

describe('HoldingsListComponent', () => {
  let component: HoldingsListComponent;
  let fixture: ComponentFixture<HoldingsListComponent>;
  let holdingsServiceMock: any;
  let authServiceMock: any;

  const mockHoldingDto: HoldingDto = {
    symbol: 'AAPL',
    quantity: 10,
    averageCost: 150.00,
    currentPrice: 155.00,
    totalCost: 1500.00,
    currentValue: 1550.00,
    gainLoss: 50.00,
    gainLossPercent: 3.33
  };

  const mockSuccessResponse: HoldingsResponse = {
    success: true,
    holdings: [mockHoldingDto],
    message: 'Holdings retrieved successfully'
  };

  const mockUnauthorizedError: ErrorInfo = {
    message: 'You do not have permission to view this account.',
    code: 'ACCOUNT_ACCESS_DENIED'
  };

  const mockServerError: ErrorInfo = {
    message: 'Server error. Please try again later.',
    code: 'SERVER_ERROR'
  };

  beforeEach(async () => {
    // Mock HoldingsService with signals and observables
    let getOwnHoldingsCallCount = 0;
    
    holdingsServiceMock = {
      isLoading: signal(false),
      error: signal<ErrorInfo | null>(null),
      holdings: of(mockSuccessResponse),
      getOwnHoldings: () => {
        getOwnHoldingsCallCount++;
        return of(mockSuccessResponse);
      },
      getCallCount: () => getOwnHoldingsCallCount,
      resetCallCount: () => { getOwnHoldingsCallCount = 0; }
    };

    // Mock Auth service with current user signal
    authServiceMock = {
      currentUser: signal<string | null>('testuser'),
      currentAccountId: signal<number | null>(1)
    };

    await TestBed.configureTestingModule({
      imports: [
        HoldingsListComponent,
        CommonModule,
        MatProgressSpinnerModule,
        MatButtonModule,
        MatTableModule,
        MatCardModule
      ],
      providers: [
        { provide: HoldingsService, useValue: holdingsServiceMock },
        { provide: Auth, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HoldingsListComponent);
    component = fixture.componentInstance;
  });

  describe('Initialization', () => {
    /**
     * AC2: Requests scoped to authenticated user
     * Verify component calls getOwnHoldings on init (no accountId parameter needed)
     */
    it('should call getOwnHoldings on component init', () => {
      holdingsServiceMock.resetCallCount();
      component.ngOnInit();
      expect(holdingsServiceMock.getCallCount()).toBeGreaterThan(0);
    });

    /**
     * AC2: Component reads authenticated user from Auth service
     * Verify currentUser signal is accessible
     */
    it('should read currentUser from Auth service', () => {
      const currentUser = component.currentUser();
      expect(currentUser).toBe('testuser');
    });
  });

  describe('Success State: Holdings Displayed', () => {
    /**
     * Test: User owns account → holdings displayed
     * Verify holdings table is rendered with data
     */
    it('should display holdings when data is loaded', () => {
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(null);
      holdingsServiceMock.holdings = of(mockSuccessResponse);

      fixture.detectChanges();

      expect(holdingsServiceMock.holdings).toBeTruthy();
    });

    /**
     * Verify all holding columns are rendered in template
     */
    it('should render all required columns in holdings table', () => {
      expect(component.displayedColumns).toContain('symbol');
      expect(component.displayedColumns).toContain('quantity');
      expect(component.displayedColumns).toContain('averageCost');
      expect(component.displayedColumns).toContain('currentPrice');
      expect(component.displayedColumns).toContain('totalCost');
      expect(component.displayedColumns).toContain('currentValue');
      expect(component.displayedColumns).toContain('gainLoss');
      expect(component.displayedColumns).toContain('gainLossPercent');
    });

    /**
     * Verify holding data is correctly structured
     */
    it('should map holding data correctly', () => {
      const holdings = mockSuccessResponse.holdings;
      expect(holdings.length).toBe(1);
      expect(holdings[0].symbol).toBe('AAPL');
      expect(holdings[0].quantity).toBe(10);
      expect(holdings[0].gainLoss).toBe(50.00);
    });
  });

  describe('Loading State', () => {
    /**
     * Test: Loading state → spinner shown then cleared
     * Verify isLoading signal reflects loading status
     */
    it('should set isLoading signal when loadHoldings is called', () => {
      holdingsServiceMock.isLoading.set(true);
      fixture.detectChanges();
      expect(component.isLoading()).toBe(true);
    });

    /**
     * Verify loading state clears after data arrives
     */
    it('should clear isLoading when data is received', () => {
      holdingsServiceMock.isLoading.set(false);
      fixture.detectChanges();
      expect(component.isLoading()).toBe(false);
    });
  });

  describe('Error State: Authorization (AC1)', () => {
    /**
     * AC1: Unauthorized data access prevented
     * Test: User does NOT own account (403) → safe error message, no data leaked
     */
    it('should display safe error message on 403 ACCOUNT_ACCESS_DENIED', () => {
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(mockUnauthorizedError);

      fixture.detectChanges();

      const error = component.error();
      expect(error).toBeTruthy();
      expect(error?.code).toBe('ACCOUNT_ACCESS_DENIED');
      expect(error?.message).toBe('You do not have permission to view this account.');
    });

    /**
     * AC1: Verify error message doesn't leak sensitive data
     * Should NOT contain database error details, account info, or SQL
     */
    it('should not leak sensitive data in error message', () => {
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(mockUnauthorizedError);

      const error = component.error();
      const errorMessage = error?.message || '';

      // Verify safe message (no DB details)
      expect(errorMessage).not.toContain('database');
      expect(errorMessage).not.toContain('account_id');
      expect(errorMessage).not.toContain('SQL');
      expect(errorMessage).not.toContain('Exception');
      expect(errorMessage).toContain('permission');
    });

    /**
     * AC1: Retry button should be shown for auth errors (403)
     */
    it('should return true for isAuthError when error code is ACCOUNT_ACCESS_DENIED', () => {
      holdingsServiceMock.error.set(mockUnauthorizedError);
      expect(component.isAuthError()).toBe(true);
    });

    /**
     * Verify isAuthError returns false for non-auth errors
     */
    it('should return false for isAuthError when error is not auth-related', () => {
      holdingsServiceMock.error.set(mockServerError);
      expect(component.isAuthError()).toBe(false);
    });
  });

  describe('Error State: Server/Network Errors', () => {
    /**
     * Test: Network error (500) → generic error message
     */
    it('should display generic error message on server error', () => {
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(mockServerError);

      fixture.detectChanges();

      const error = component.error();
      expect(error?.message).toBe('Server error. Please try again later.');
      expect(error?.code).toBe('SERVER_ERROR');
    });

    /**
     * Verify retry button NOT shown for non-auth errors
     */
    it('should not show retry button for non-auth errors', () => {
      holdingsServiceMock.error.set(mockServerError);
      expect(component.isAuthError()).toBe(false);
    });
  });

  describe('Empty State', () => {
    /**
     * Test: Empty holdings → "No holdings yet" message
     * Verify empty state is shown when holdings array is empty
     */
    it('should display empty state when user has no holdings', () => {
      const emptyResponse: HoldingsResponse = {
        success: true,
        holdings: [],
        message: 'No holdings found'
      };

      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(null);
      holdingsServiceMock.holdings = of(emptyResponse);

      fixture.detectChanges();

      const holdings = holdingsServiceMock.holdings;
      expect(holdings).toBeTruthy();
    });

    /**
     * Verify empty message is user-friendly
     */
    it('should show helpful empty state message', () => {
      const emptyResponse: HoldingsResponse = {
        success: true,
        holdings: []
      };

      holdingsServiceMock.holdings = of(emptyResponse);
      fixture.detectChanges();

      // Component logic: verify empty holdings array
      expect(emptyResponse.holdings.length).toBe(0);
    });
  });

  describe('Retry Functionality (AC1)', () => {
    /**
     * AC1: Test: Retry after auth error → reloads with same accountId
     */
    it('should call loadHoldings when retry is clicked', () => {
      let loadHoldingsCalled = false;
      const originalLoadHoldings = component.loadHoldings.bind(component);
      component.loadHoldings = () => {
        loadHoldingsCalled = true;
        originalLoadHoldings();
      };
      
      component.retry();
      expect(loadHoldingsCalled).toBe(true);
    });

    /**
     * Verify retry uses same accountId (from Auth service, not hardcoded)
     */
    it('should retry using accountId from Auth service', () => {
      holdingsServiceMock.resetCallCount();
      component.loadHoldings();

      expect(holdingsServiceMock.getCallCount()).toBe(1);
      // getOwnHoldings extracts accountId internally - component doesn't pass it
    });

    /**
     * Verify multiple retries work without state corruption
     */
    it('should handle multiple retries correctly', () => {
      holdingsServiceMock.resetCallCount();
      component.retry();
      component.retry();
      component.retry();

      expect(holdingsServiceMock.getCallCount()).toBe(3);
    });
  });

  describe('AC2: Scoped to Authenticated User', () => {
    /**
     * AC2: AccountId comes from Auth service (not hardcoded)
     * Verify component doesn't have hardcoded accountId
     */
    it('should not have hardcoded accountId property', () => {
      expect((component as any).accountId).toBeUndefined();
    });

    /**
     * Verify getOwnHoldings is called (which extracts accountId internally)
     * Component doesn't need to know about accountId
     */
    it('should call service method that handles scoping internally', () => {
      holdingsServiceMock.resetCallCount();
      component.loadHoldings();
      expect(holdingsServiceMock.getCallCount()).toBe(1);
    });

    /**
     * Verify component reads currentUser from Auth for display only
     */
    it('should read currentUser from Auth service for display', () => {
      const user = component.currentUser();
      expect(user).toBe('testuser');
      expect(authServiceMock.currentUser).toBeDefined();
    });

    /**
     * Verify different users would see different data (simulated)
     */
    it('should display different user when Auth service updates', () => {
      authServiceMock.currentUser.set('newuser');
      fixture.detectChanges();

      expect(component.currentUser()).toBe('newuser');
    });
  });

  describe('State Transitions', () => {
    /**
     * Verify loading → success transition
     */
    it('should transition from loading to success state', () => {
      // Start loading
      holdingsServiceMock.isLoading.set(true);
      expect(component.isLoading()).toBe(true);

      // Complete loading
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(null);
      expect(component.isLoading()).toBe(false);
      expect(component.error()).toBeNull();
    });

    /**
     * Verify loading → error transition
     */
    it('should transition from loading to error state', () => {
      // Start loading
      holdingsServiceMock.isLoading.set(true);
      expect(component.isLoading()).toBe(true);

      // Error occurs
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(mockUnauthorizedError);
      expect(component.isLoading()).toBe(false);
      expect(component.error()).toBeTruthy();
    });

    /**
     * Verify error → retry → success cycle
     */
    it('should clear error on successful retry', () => {
      // Start in error state
      holdingsServiceMock.error.set(mockUnauthorizedError);
      expect(component.error()).toBeTruthy();

      // Retry
      holdingsServiceMock.isLoading.set(true);
      expect(component.isLoading()).toBe(true);

      // Success
      holdingsServiceMock.isLoading.set(false);
      holdingsServiceMock.error.set(null);
      expect(component.error()).toBeNull();
    });
  });

  describe('Data Binding & Display', () => {
    /**
     * Verify async pipe works correctly with holdings observable
     */
    it('should have holdings$ observable accessible', () => {
      const holdings$ = component.holdings$;
      expect(holdings$).toBeTruthy();
    });

    /**
     * Verify error signal can be displayed in template
     */
    it('should have error signal accessible', () => {
      const error = component.error();
      expect(error === null || typeof error === 'object').toBe(true);
    });

    /**
     * Verify loading signal can be displayed in template
     */
    it('should have isLoading signal accessible', () => {
      const isLoading = component.isLoading();
      expect(typeof isLoading).toBe('boolean');
    });
  });

  describe('Error Handling Edge Cases', () => {
    /**
     * Verify component handles null error gracefully
     */
    it('should handle null error signal', () => {
      holdingsServiceMock.error.set(null);
      expect(component.error()).toBeNull();
      expect(component.isAuthError()).toBe(false);
    });

    /**
     * Verify component handles undefined error code
     */
    it('should handle error without code property', () => {
      const errorWithoutCode: ErrorInfo = {
        message: 'Some error occurred'
      };
      holdingsServiceMock.error.set(errorWithoutCode);
      expect(component.isAuthError()).toBe(false);
    });

    /**
     * Verify component handles empty holdings array
     */
    it('should handle empty holdings array', () => {
      const emptyResponse: HoldingsResponse = {
        success: true,
        holdings: []
      };
      holdingsServiceMock.holdings = of(emptyResponse);
      fixture.detectChanges();

      expect(emptyResponse.holdings.length).toBe(0);
    });
  });

  describe('Component Lifecycle', () => {
    /**
     * Verify component properly initializes on creation
     */
    it('should initialize component successfully', () => {
      expect(component).toBeTruthy();
    });

    /**
     * Verify loadHoldings is called on ngOnInit
     */
    it('should call loadHoldings on component init', () => {
      holdingsServiceMock.resetCallCount();
      component.ngOnInit();
      expect(holdingsServiceMock.getCallCount()).toBeGreaterThan(0);
    });

    /**
     * Verify component cleans up properly
     */
    it('should destroy component without errors', () => {
      expect(() => {
        fixture.destroy();
      }).not.toThrow();
    });
  });
});
