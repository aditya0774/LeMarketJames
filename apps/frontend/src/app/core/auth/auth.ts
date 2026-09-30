import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';

export interface RegisterRequest {
  // Registration payload aligned with the Spring Boot RegisterRequest DTO.
  username: string;
  password: string;
  email: string;
  fullName: string;
  streetAddress: string;
  apartment?: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
  ssn: string;
  initialDeposit: number;
  investmentExperience: 'beginner' | 'experienced';
  employmentStatus: string;
  dateOfBirth: string;
  phoneNumber: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Who a logged-in user is (contract C7, contracts/C7-roles.md). Mirrors the backend enum
 * com.lemarketjames.common.security.Role; change both together.
 */
export type Role = 'CLIENT' | 'TRADING_OPS' | 'ANALYST' | 'COMPLIANCE';

/** What /login and /me say about the session. accountId is only present for clients. */
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

  // Reflects login state; the JWT itself lives only in the httpOnly cookie.
  readonly currentUser = signal<string | null>(null);
  
  // AC2: Stores authenticated user's account ID (extracted from login response)
  readonly currentAccountId = signal<number | null>(null);

  // The session's roles, for showing or hiding staff-only views. The backend enforces them anyway.
  readonly roles = signal<readonly Role[]>([]);

  constructor(private readonly http: HttpClient) {}

  register(request: RegisterRequest): Promise<AuthResponse> {
    return firstValueFrom(this.http.post<AuthResponse>(`${this.baseUrl}/register`, request));
  }

  async login(request: LoginRequest): Promise<AuthResponse> {
    const response = await firstValueFrom(this.http.post<AuthResponse>(`${this.baseUrl}/login`, request));
    this.applySession(response);
    return response;
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.http.post(`${this.baseUrl}/logout`, {}));
    this.applySession(null);
  }

  // Restores login state after a page refresh by checking the httpOnly cookie with the backend.
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
    this.currentAccountId.set(session?.accountId ?? null);
    this.roles.set(session?.roles ?? []);
  }
}
