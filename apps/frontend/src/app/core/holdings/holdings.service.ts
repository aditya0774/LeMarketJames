import { Injectable, signal, effect } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, BehaviorSubject, catchError, finalize, tap, filter } from 'rxjs';
import { Auth } from '../auth/auth';
import { HoldingsResponse, ValidateHoldingRequest, ValidateHoldingResponse } 
  from '@app/shared/models/holdings.model';

/**
 * Structured error information
 * AC1: Error details include code for distinguishing authorization vs other errors
 */
export interface ErrorInfo {
  message: string;
  code?: string;
}

/**
 * Service layer for holdings management.
 * 
 * AC1: Unauthorized data access prevented - validates ownership via 403 responses
 * AC2: Requests scoped to authenticated user - extracts accountId from Auth service
 * 
 * Uses reactive patterns with signals for loading/error state and Observables for data.
 */
@Injectable({ providedIn: 'root' })
export class HoldingsService {
  
  private readonly API_URL = '/api/v1/holdings';
  
  // AC2: Reactive state signals for loading and error status
  readonly isLoading = signal(false);
  readonly error = signal<ErrorInfo | null>(null);
  
  // Observable stream for holdings data
  private readonly holdings$ = new BehaviorSubject<HoldingsResponse | null>(null);
  readonly holdings = this.holdings$.asObservable();
  
  constructor(
    private readonly http: HttpClient,
    private readonly authService: Auth
  ) {
    // Cached private data must never survive a change of authenticated account.
    let previousAccount = this.authService.currentAccountId();
    effect(() => {
      const account = this.authService.currentAccountId();
      if (account !== previousAccount) {
        previousAccount = account;
        this.holdings$.next(null);
        this.error.set(null);
      }
    });
  }
  
  /**
   * AC1: Retrieve holdings for the authenticated user
   * AC2: Requests scoped to authenticated user
   * 
   * Automatically extracts and uses the authenticated user's accountId from Auth service.
   * Manages loading and error states via signals.
   * 
   * @return Observable<HoldingsResponse> containing user's holdings
   * @throws Error if no authenticated account found or HTTP error occurs
   */
  getOwnHoldings(): Observable<HoldingsResponse> {
    // AC2: Extract accountId from authenticated user context (signal)
    const accountIdSignal = this.authService.currentAccountId;
    const accountId = typeof accountIdSignal === 'function' ? accountIdSignal() : accountIdSignal;
    
    if (!accountId) {
      this.holdings$.next(null);
      const errorMsg: ErrorInfo = {
        message: 'No authenticated account found. Please log in.',
        code: 'NO_ACCOUNT_ID'
      };
      this.error.set(errorMsg);
      throw new Error(errorMsg.message);
    }
    
    this.holdings$.next(null);
    this.isLoading.set(true);
    this.error.set(null);
    
    return this.http.get<HoldingsResponse>(`${this.API_URL}?accountId=${accountId}`)
      .pipe(
        // Ignore a late response from a previous login.
        filter(() => this.authService.currentAccountId() === accountId),
        tap(response => {
          this.holdings$.next(response);
          this.error.set(null);
        }),
        catchError((err: HttpErrorResponse) => {
          const errorInfo = this.handleError(err);
          if (this.authService.currentAccountId() === accountId) {
            this.error.set(errorInfo);
          }
          throw err;
        }),
        finalize(() => {
          this.isLoading.set(false);
        })
      );
  }
  
  /**
   * AC2: Validate sufficient holdings before SELL order
   * AC1: Unauthorized data access prevented - validates ownership via 403 responses
   * 
   * Validates that the authenticated user owns the account and has sufficient holdings.
   * Automatically extracts accountId from Auth service.
   * 
   * @param instrumentId the instrument ID to validate holdings for
   * @param sellQuantity the quantity attempting to be sold
   * @return Observable<ValidateHoldingResponse> indicating success or failure
   * @throws Error if no authenticated account found or HTTP error occurs
   */
  validateOwnHoldings(instrumentId: number, sellQuantity: number): Observable<ValidateHoldingResponse> {
    // AC2: Extract accountId from authenticated user context (signal)
    const accountIdSignal = this.authService.currentAccountId;
    const accountId = typeof accountIdSignal === 'function' ? accountIdSignal() : accountIdSignal;
    
    if (!accountId) {
      this.holdings$.next(null);
      const errorMsg: ErrorInfo = {
        message: 'No authenticated account found. Please log in.',
        code: 'NO_ACCOUNT_ID'
      };
      this.error.set(errorMsg);
      throw new Error(errorMsg.message);
    }
    
    this.holdings$.next(null);
    this.isLoading.set(true);
    this.error.set(null);
    
    const request: ValidateHoldingRequest = {
      accountId,
      instrumentId,
      sellQuantity
    };
    
    return this.http.post<ValidateHoldingResponse>(`${this.API_URL}/validate`, request)
      .pipe(
        tap(response => {
          this.error.set(null);
        }),
        catchError((err: HttpErrorResponse) => {
          const errorInfo = this.handleError(err);
          if (this.authService.currentAccountId() === accountId) {
            this.error.set(errorInfo);
          }
          throw err;
        }),
        finalize(() => {
          this.isLoading.set(false);
        })
      );
  }
  
  /**
   * AC1: Error handling with authorization detection
   * 
   * Converts HTTP error responses to user-safe error messages.
   * Detects 403 ACCOUNT_ACCESS_DENIED errors specifically.
   * Does NOT leak sensitive database or account details to user.
   * 
   * @param err the HTTP error response
   * @return ErrorInfo with safe message and error code
   */
  private handleError(err: HttpErrorResponse): ErrorInfo {
    // AC1: Handle 403 Unauthorized access specifically (ACCOUNT_ACCESS_DENIED)
    if (err.status === 403) {
      const body = err.error;
      if (body?.code === 'ACCOUNT_ACCESS_DENIED') {
        return {
          message: 'You do not have permission to view this account.',
          code: 'ACCOUNT_ACCESS_DENIED'
        };
      }
      return {
        message: 'Access denied.',
        code: 'FORBIDDEN'
      };
    }
    
    // Handle 400 Bad Request (e.g., insufficient holdings)
    if (err.status === 400) {
      return {
        message: err.error?.error || 'Invalid request.',
        code: err.error?.code || 'BAD_REQUEST'
      };
    }
    
    // Handle 401 Unauthorized (auth token issue)
    if (err.status === 401) {
      return {
        message: 'Authentication required. Please log in.',
        code: 'UNAUTHORIZED'
      };
    }
    
    // Handle 500+ server errors
    if (err.status >= 500) {
      return {
        message: 'Server error. Please try again later.',
        code: 'SERVER_ERROR'
      };
    }
    
    // Network or unknown error
    return {
      message: 'An error occurred. Please try again.',
      code: 'UNKNOWN_ERROR'
    };
  }
}
