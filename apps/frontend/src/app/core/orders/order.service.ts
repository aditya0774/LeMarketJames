import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, defer, map, of, scan, switchMap, throwError, timer } from 'rxjs';
import { HoldingsService } from '../holdings/holdings.service';
import { Auth } from '../auth/auth';
import { environment } from '../../../environments/environment';

export type OrdersConnectionState = 'connecting' | 'live' | 'reconnecting';

export interface OrdersWatchSnapshot {
  orders: OrderResponse[];
  connection: OrdersConnectionState;
}

export interface OrderStatusChangedEvent {
  orderId: number;
  accountId: number;
  from: OrderResponse['orderStatus'];
  to: OrderResponse['orderStatus'];
  occurredAt: string;
}

export interface OrdersStreamSnapshot {
  connection: OrdersConnectionState;
  event?: OrderStatusChangedEvent;
}

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
  success: boolean;
  reason?: string | null;
  code?: string | null;
  orderId: number;
  accountId: number;
  instrumentId: number;
  orderType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit?: number | null;
  orderStatus: 'SUBMITTED' | 'ACCEPTED' | 'PENDING' | 'FILLED' | 'REJECTED' | 'DELAYED';
  rejectionReason?: string | null;
  submittedAt: string;
  acceptedAt?: string | null;
  filledAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** The backend stores UTC but currently serializes LocalDateTime without an offset. */
export function orderTimestamp(timestamp: string): string {
  return /(?:Z|[+-]\d{2}:?\d{2})$/i.test(timestamp) ? timestamp : `${timestamp}Z`;
}

export interface OrderHistoryFilter {
  date: string;
  timeZone: string;
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private readonly apiUrl = `${environment.apiBaseUrl}/api/v1/orders`;

  constructor(
    private readonly http: HttpClient,
    private readonly holdingsService: HoldingsService,
    private readonly auth: Auth
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
    return this.http.post<OrderResponse>(`${environment.apiBaseUrl}/api/v1/buy-orders`, request);
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
  getOrdersByAccountId(accountId: number, filter?: OrderHistoryFilter): Observable<OrderResponse[]> {
    const params = filter ? { date: filter.date, timeZone: filter.timeZone } : undefined;
    return this.http.get<OrderResponse[]>(`${this.apiUrl}/account/${accountId}`, { params }).pipe(
      map(orders => orders.map(order => ({ ...order, submittedAt: orderTimestamp(order.submittedAt) }))),
    );
  }

  /**
   * Watch backend order-status SSE events for the signed-in account.
   */
  watchOrderStatusStream(): Observable<OrdersStreamSnapshot> {
    return new Observable<OrdersStreamSnapshot>((subscriber) => {
      subscriber.next({ connection: 'connecting' });

      const stream = new EventSource(`${this.apiUrl}/stream`);

      stream.onopen = () => subscriber.next({ connection: 'live' });
      stream.onerror = () => subscriber.next({ connection: 'reconnecting' });
      stream.addEventListener('order-status-changed', (event) => {
        const payload = JSON.parse((event as MessageEvent<string>).data) as OrderStatusChangedEvent;
        subscriber.next({ connection: 'live', event: payload });
      });

      return () => stream.close();
    });
  }

  /**
   * Poll account orders as a mocked live stream.
   */
  watchOrdersByAccountId(accountId: number, refreshMs: number): Observable<OrdersWatchSnapshot> {
    type PollResult = { ok: true; orders: OrderResponse[] } | { ok: false };
    return timer(0, refreshMs).pipe(
      switchMap(() => this.getOrdersByAccountId(accountId).pipe(
        map((orders): PollResult => ({ ok: true, orders })),
        catchError((error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 401) {
            return throwError(() => error);
          }
          return of<PollResult>({ ok: false });
        }),
      )),
      scan((state: OrdersWatchSnapshot, result) =>
        result.ok
          ? { orders: result.orders, connection: 'live' as const }
          : { ...state, connection: 'reconnecting' as const },
      { orders: [], connection: 'connecting' as const }),
    );
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
