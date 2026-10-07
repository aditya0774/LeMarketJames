import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../../core/auth/auth';
import { homeFor } from '../../../core/auth/staff-home';

/** Shown to a client who signs in here; their session is ended before it is shown. */
export const STAFF_ONLY_MESSAGE = 'This login is for staff only';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'staff-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
/**
 * Staff Login Component
 *
 * Renders the login form for staff (Trading Ops and Analyst roles).
 * Uses the same LeMarket auth styles as the trading app.
 */
export class Login {
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly form: ReturnType<FormBuilder['group']>;
  protected showPassword = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: Auth,
    private readonly router: Router,
  ) {
    this.form = this.fb.group({
      username: ['', Validators.required],
      password: ['', Validators.required],
    });
  }

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);
    try {
      await this.auth.login(this.form.value.username, this.form.value.password);
      if (this.auth.hasRole('CLIENT')) {
        // Clients and staff share one sign-in endpoint (C7), so a client's credentials work here
        // and the gateway has already given this browser a staff_jwt. Nothing in this app is
        // for clients: sign out again so it doesn't keep one.
        await this.auth.logout();
        this.errorMessage.set(STAFF_ONLY_MESSAGE);
        return;
      }
      await this.router.navigate([homeFor(this.auth.roles())]);
    } catch (error) {
      const serverMessage =
        error instanceof HttpErrorResponse &&
        typeof error.error === 'object' &&
        typeof error.error?.message === 'string'
          ? error.error.message
          : null;
      // A proxy or service outage must not be presented as rejected credentials.
      this.errorMessage.set(error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)
        ? 'Staff login is unavailable. Please try again when the staff service is running.'
        : error instanceof HttpErrorResponse && error.status === 401
          ? 'Invalid email or password.'
          : serverMessage ?? 'Unable to log in. Please try again.');
    } finally {
      this.submitting.set(false);
    }
  }

  protected togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }
}
