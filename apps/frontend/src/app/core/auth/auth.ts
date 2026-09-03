import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  fullName: string;
  streetAddress: string;
  apartment: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
  ssn: string;
  initialDeposit: number;
  investmentExperience: 'beginner' | 'experienced';
  dateOfBirth: string;
  phoneNumber: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

interface AuthResponse {
  username: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/auth`;

  // Reflects login state; the JWT itself lives only in the httpOnly cookie.
  readonly currentUser = signal<string | null>(null);

  constructor(private readonly http: HttpClient) {}

  register(request: RegisterRequest): Promise<AuthResponse> {
    return firstValueFrom(this.http.post<AuthResponse>(`${this.baseUrl}/register`, request));
  }

  async login(request: LoginRequest): Promise<AuthResponse> {
    const response = await firstValueFrom(this.http.post<AuthResponse>(`${this.baseUrl}/login`, request));
    this.currentUser.set(response.username);
    return response;
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.http.post(`${this.baseUrl}/logout`, {}));
    this.currentUser.set(null);
  }

  // Restores login state after a page refresh by checking the httpOnly cookie with the backend.
  async restoreSession(): Promise<void> {
    try {
      const response = await firstValueFrom(this.http.get<{ username: string }>(`${this.baseUrl}/me`));
      this.currentUser.set(response.username);
    } catch {
      this.currentUser.set(null);
    }
  }
}
