import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BalanceResponse } from '../../shared/models/order.model';

export type CashMovement = 'DEPOSIT' | 'WITHDRAW';

/**
 * Moves money in and out of the signed-in client's account (S42, C6). Each answer carries
 * the new balance in the `GET /api/balance` shape, so the page shows the new buying power at once.
 *
 * `requestId` makes a retry safe: the server applies a given id once, so a request that timed out
 * can be sent again without moving the money twice.
 */
@Injectable({ providedIn: 'root' })
export class CashService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/portfolio`;

  move(kind: CashMovement, amount: number, requestId: string): Promise<BalanceResponse> {
    const path = kind === 'DEPOSIT' ? 'deposits' : 'withdrawals';
    return firstValueFrom(this.http.post<BalanceResponse>(`${this.baseUrl}/${path}`, { amount, requestId }));
  }
}
