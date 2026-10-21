import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/** What is left on the client's account, and whether it may be closed now (C6, GET /api/auth/close-account). */
export interface AccountContents {
  closable: boolean;
  /** Stays on the closed account; operations pay it out. */
  cashBalance: number;
  currency: string;
  /** How many stocks the account holds shares of. */
  holdings: number;
  /** How many orders are not yet filled or rejected. */
  openOrders: number;
}

/** The ?reason= that tells the login page the client has just closed their own account. */
export const ACCOUNT_CLOSED_REASON = 'closed';

/** What the login page says after a client closes their account. */
export const ACCOUNT_CLOSED_MESSAGE = {
  heading: 'Account closed',
  message: 'Your account is closed and you are signed out. Any cash left on it will be paid out to you by our operations team.',
};

/**
 * A client closing their own account (BR-03). auth-service decides: it refuses while stocks or
 * open orders remain, checks the password again, and clears the session cookie when it closes.
 */
@Injectable({ providedIn: 'root' })
export class AccountClosure {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/auth/close-account`;

  check(): Promise<AccountContents> {
    return firstValueFrom(this.http.get<AccountContents>(this.url));
  }

  close(password: string): Promise<{ message: string; cashBalance: number; currency: string }> {
    return firstValueFrom(this.http.post<{ message: string; cashBalance: number; currency: string }>(this.url, { password }));
  }
}
