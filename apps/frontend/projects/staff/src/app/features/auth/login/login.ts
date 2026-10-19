import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../../core/auth/auth';
import { homeFor } from '../../../core/auth/staff-home';
import { INACTIVE_REASON, isAccountInactive } from '../../../core/auth/account-inactive';
import { Brand } from '../../../shared/components/brand/brand';

/** Shown to a client who signs in here; their session is ended before it is shown. */
export const STAFF_ONLY_MESSAGE = 'This login is for staff only';

/** Shown, with the heading "Account not active", for a staff login that has been deactivated (C6). */
export const ACCOUNT_INACTIVE_MESSAGE = 'This account is no longer active. Please contact the firm.';

/** How the message box looks: err for a failure, warn for a lockout, info for a pointer elsewhere. */
export type LoginAlertKind = 'err' | 'warn' | 'info';

/** The auth service words both lockout responses this way ("Account locked for …", "Account temporarily locked. Try again in …"). */
const LOCKOUT_PREFIX = /^Account (temporarily )?locked/;

@Component({
  imports: [ReactiveFormsModule, Brand],
  selector: 'staff-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
/**
 * Staff Login Component
 *
 * Renders the login form for staff (Trading Ops and Analyst roles), laid out like the redesign's
 * "Staff log in" board: what Courtside View is on the left, the form on the right. Messages follow
 * the trading app's log in: red when it failed, amber for a lockout, purple for a client login.
 */
export class Login {
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly errorHeading = signal('Login failed');
  protected readonly errorKind = signal<LoginAlertKind>('err');
  protected readonly submitting = signal(false);
  protected readonly form: ReturnType<FormBuilder['group']>;
  protected showPassword = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: Auth,
    private readonly router: Router,
    route: ActivatedRoute,
  ) {
    this.form = this.fb.group({
      username: ['', Validators.required],
      password: ['', Validators.required],
    });
    // Sent here by accountInactiveInterceptor after the account was cut off mid-session.
    if (route.snapshot.queryParamMap.get('reason') === INACTIVE_REASON) {
      this.showAccountInactive();
    }
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
        this.showAlert('info', 'Client account', STAFF_ONLY_MESSAGE);
        return;
      }
      await this.router.navigate([homeFor(this.auth.roles())]);
    } catch (error) {
      if (isAccountInactive(error)) {
        this.showAccountInactive();
        return;
      }
      const message = this.loginErrorMessage(error);
      if (LOCKOUT_PREFIX.test(message)) {
        this.showAlert('warn', 'Account locked', message);
        return;
      }
      this.showAlert('err', 'Login failed', message);
    } finally {
      this.submitting.set(false);
    }
  }

  private loginErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      // A proxy or service outage must not be presented as rejected credentials.
      if (error.status === 0 || error.status >= 500) {
        return 'Staff login is unavailable. Please try again when the staff service is running.';
      }
      if (error.status === 401) {
        return 'Invalid email or password.';
      }
      if (typeof error.error === 'object' && typeof error.error?.message === 'string') {
        return error.error.message;
      }
    }
    return 'Unable to log in. Please try again.';
  }

  private showAccountInactive(): void {
    this.showAlert('err', 'Account not active', ACCOUNT_INACTIVE_MESSAGE);
  }

  private showAlert(kind: LoginAlertKind, heading: string, message: string): void {
    this.errorKind.set(kind);
    this.errorHeading.set(heading);
    this.errorMessage.set(message);
  }

  protected togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }
}
