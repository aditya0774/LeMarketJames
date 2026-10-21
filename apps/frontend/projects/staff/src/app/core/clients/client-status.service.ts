import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * A client's standing (contract C7). Mirrors com.lemarketjames.common.domain.AccountStatus;
 * change both together.
 */
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | 'EXPIRED';

/** One change in a client's status history (C6, GET /api/v1/clients/{clientId}/status). */
export interface StatusChange {
  oldStatus: AccountStatus;
  newStatus: AccountStatus;
  reason: string;
  changedBy: string;
  /** ISO-8601 UTC. */
  changedAt: string;
}

export interface ClientStatus {
  clientId: number;
  fullName: string;
  status: AccountStatus;
  /** Newest first. */
  changes: StatusChange[];
}

/**
 * Trading Ops' view and change of a client's account status (BR-03). auth-service allows it for
 * TRADING_OPS only; the staff app's proxy routes it there.
 */
@Injectable({ providedIn: 'root' })
export class ClientStatusService {
  private readonly http = inject(HttpClient);

  status(clientId: number) {
    return this.http.get<ClientStatus>(this.url(clientId));
  }

  change(clientId: number, status: AccountStatus, reason: string) {
    return this.http.put<ClientStatus>(this.url(clientId), { status, reason });
  }

  private url(clientId: number): string {
    return `${environment.apiBaseUrl}/api/v1/clients/${clientId}/status`;
  }
}
