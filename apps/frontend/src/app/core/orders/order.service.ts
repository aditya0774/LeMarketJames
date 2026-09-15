import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OrderRequest {
  accountId: number;
  instrumentId: number;
  orderType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit?: number;
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

  constructor(private http: HttpClient) {}

  /**
   * Create a new order
   */
  createOrder(request: OrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(this.apiUrl, request);
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
