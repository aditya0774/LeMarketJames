import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, defer, switchMap, throwError } from 'rxjs';
import { HoldingsService } from '../holdings/holdings.service';
import { Auth } from '../auth/auth';

export interface OrderRequest {
  accountId: number;
  instrumentId: number;
  orderType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit?: number;
}

export interface BuyOrderRequest {
  accountId: number;
  instrumentId: number;
  quantity: number;
  pricePerUnit: number;
}

export interface OrderResponse {
  orderId: number;
  accountId: number;
  instrumentId: number;
  orderType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit?: number;
  orderStatus: 'SUBMITTED' | 'ACCEPTED' | 'PENDING' | 'FILLED' | 'REJECTED' | 'DELAYED';
  rejectionReason?: string;
  submittedAt: string;
  acceptedAt?: string;
  filledAt?: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = '/api/v1/orders';

  constructor(
    private http: HttpClient,
    private holdingsService: HoldingsService,
    private auth: Auth
  ) {}

  /**
   * Create a new order
   */
  createOrder(request: OrderRequest): Observable<OrderResponse> {
    // Snapshot the form values so validation and submission use the same order.
    const order = { ...request };
    if (order.orderType !== 'SELL') {
      return this.http.post<OrderResponse>(this.apiUrl, order);
    }

    return defer(() => {
      // Holdings validation uses the signed-in account, which must match the order.
      if (!this.auth.currentAccountId() || order.accountId !== this.auth.currentAccountId()) {
        return throwError(() => new Error('Please select your authenticated account before selling.'));
      }
      return this.holdingsService.validateOwnHoldings(order.instrumentId, order.quantity);
    }).pipe(
      switchMap(validation => {
        if (order.accountId !== this.auth.currentAccountId()) {
          return throwError(() => new Error('Your account changed. Please submit the order again.'));
        }
        if (!validation.success) {
          return throwError(() => new Error(validation.error || 'Unable to validate holdings.'));
        }
        return this.http.post<OrderResponse>(this.apiUrl, order);
      })
    );
  }

  /**
   * Submit a BUY order using the dedicated buy-order endpoint.
   */
  submitBuyOrder(request: BuyOrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>('/api/v1/buy-orders', request);
  }

  /**
   * Get order by ID
   */
  getOrderById(orderId: number): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.apiUrl}/${orderId}`);
  }

  /**
   * Get all orders for an account
   */
  getOrdersByAccountId(accountId: number): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`${this.apiUrl}/account/${accountId}`);
  }

  /**
   * Get orders for an account with a specific status
   */
  getOrdersByAccountAndStatus(accountId: number, status: string): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`${this.apiUrl}/account/${accountId}/status/${status}`);
  }

  /**
   * Get all orders for an instrument
   */
  getOrdersByInstrumentId(instrumentId: number): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`${this.apiUrl}/instrument/${instrumentId}`);
  }

  /**
   * Update order status
   */
  updateOrderStatus(orderId: number, status: string): Observable<OrderResponse> {
    return this.http.put<OrderResponse>(`${this.apiUrl}/${orderId}/status/${status}`, {});
  }

  /**
   * Reject an order
   */
  rejectOrder(orderId: number, reason?: string): Observable<OrderResponse> {
    const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.http.post<OrderResponse>(`${this.apiUrl}/${orderId}/reject${params}`, {});
  }
}
