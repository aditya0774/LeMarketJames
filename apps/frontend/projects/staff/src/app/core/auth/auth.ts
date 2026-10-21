import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';
import type { SessionLease } from '@lemarket/shared-ui';

export type Role = 'CLIENT' | 'TRADING_OPS' | 'ANALYST';

/** What /login and /me say about the session; the timing (BR-03) is what the inactivity timeout counts down from. */
interface SessionResponse extends Partial<SessionLease> {
  username: string;
  roles?: Role[];
  accountId?: number;
  /** Staff only: their own email and full name, from staff_users (C6, LMKT-144). */
  email?: string;
  fullName?: string;
}

interface AuthResponse extends SessionResponse {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/auth`;

  readonly currentUser = signal<string | null>(null);
  readonly roles = signal<readonly Role[]>([]);
  /** The signed-in staff member's email and full name; null when the session doesn't carry them. */
  readonly email = signal<string | null>(null);
  readonly fullName = signal<string | null>(null);

  // How long the session has left as of sign-in or the page load; the inactivity timeout starts from it.
  private lease: SessionLease | null = null;

  constructor(private readonly http: HttpClient) {}

  async login(username: string, password: string): Promise<AuthResponse> {
    const response = await firstValueFrom(this.http.post<AuthResponse>(`${this.baseUrl}/login`, { username, password }));
    this.applySession(response);
    return response;
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.http.post(`${this.baseUrl}/logout`, {}));
    this.applySession(null);
  }

  async restoreSession(): Promise<void> {
    try {
      this.applySession(await firstValueFrom(this.http.get<SessionResponse>(`${this.baseUrl}/me`)));
    } catch {
      this.applySession(null);
    }
  }

  /** Extends the session while the user is active (BR-03); the new cookie replaces the old one. */
  refresh(): Promise<SessionLease> {
    return firstValueFrom(this.http.post<SessionLease>(`${this.baseUrl}/refresh`, {}));
  }

  /** The session's timing as of sign-in or the page load, or null when signed out. */
  sessionLease(): SessionLease | null {
    return this.lease;
  }

  // Forgets the session locally when the server has already ended it (an account cut off
  // mid-use, LMKT-56); there is nothing left to log out of, so no request is sent.
  clearSession(): void {
    this.applySession(null);
  }

  hasRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  private applySession(session: SessionResponse | null): void {
    this.currentUser.set(session?.username ?? null);
    this.roles.set(session?.roles ?? []);
    this.email.set(session?.email ?? null);
    this.fullName.set(session?.fullName ?? null);
    this.lease = session?.expiresInSeconds !== undefined && session.inactivityTimeoutSeconds !== undefined
      ? { expiresInSeconds: session.expiresInSeconds, inactivityTimeoutSeconds: session.inactivityTimeoutSeconds }
      : null;
  }
}
