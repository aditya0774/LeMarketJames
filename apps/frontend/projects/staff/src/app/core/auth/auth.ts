import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';

export type Role = 'CLIENT' | 'TRADING_OPS' | 'ANALYST';

interface SessionResponse {
  username: string;
  roles?: Role[];
  accountId?: number;
}

interface AuthResponse extends SessionResponse {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/auth`;

  readonly currentUser = signal<string | null>(null);
  readonly roles = signal<readonly Role[]>([]);

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

  hasRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  private applySession(session: SessionResponse | null): void {
    this.currentUser.set(session?.username ?? null);
    this.roles.set(session?.roles ?? []);
  }
}
