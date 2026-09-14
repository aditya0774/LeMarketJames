import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';
import {
  OrderRequest,
  OrderResponse,
  BalanceResponse,
  OrdersListResponse,
  OrderDetailsResponse,
} from '../../shared/models/order.model';

/**
 * OrdersService
 *
 * Manages all order-related HTTP calls and balance retrieval.
 * Follows the same pattern as the Auth service: uses HttpClient,
 * returns Promises via firstValueFrom, and interacts with the backend API.
 */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Fetches the current user's account balance and portfolio summary.
   * Called before displaying the order form to show available cash.
   */
  getBalance(): Promise<BalanceResponse> {
    return firstValueFrom(this.http.get<BalanceResponse>(`${this.baseUrl}/balance`));
  }

  /**
   * Creates a new order with the provided details.
   * Backend validates balance; frontend should also validate to prevent unnecessary API calls.
   */
  createOrder(request: OrderRequest): Promise<OrderResponse> {
    return firstValueFrom(this.http.post<OrderResponse>(`${this.baseUrl}/orders`, request));
  }

  /**
   * Retrieves all orders for the authenticated user.
   * Optional query params: status, limit, offset
   */
  getOrders(filters?: { status?: string; limit?: number; offset?: number }): Promise<OrdersListResponse> {
    let url = `${this.baseUrl}/orders`;
    const params = new URLSearchParams();
    if (filters?.status) params.append('status', filters.status);
    if (filters?.limit) params.append('limit', filters.limit.toString());
    if (filters?.offset) params.append('offset', filters.offset.toString());
    if (params.toString()) url += `?${params.toString()}`;
    return firstValueFrom(this.http.get<OrdersListResponse>(url));
  }

  /**
   * Retrieves a specific order by ID.
   */
  getOrder(orderId: string): Promise<OrderDetailsResponse> {
    return firstValueFrom(this.http.get<OrderDetailsResponse>(`${this.baseUrl}/orders/${orderId}`));
  }
}
