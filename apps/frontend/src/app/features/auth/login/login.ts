import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../../core/auth/auth';
import { INACTIVE_REASON, isAccountInactive } from '../../../core/auth/account-inactive';
import { Brand } from '../../../shared/components/brand/brand';
import { sessionEndMessage } from '@lemarket/shared-ui';

/** Shown, with the heading "Account not active", for an account the firm has closed or suspended (C6). */
export const ACCOUNT_INACTIVE_MESSAGE = 'This account is no longer active. Please contact the firm.';

/** How the message box looks: err for a failure, warn for a lockout, info for a pointer elsewhere. */
export type LoginAlertKind = 'err' | 'warn' | 'info';

/** The auth service words both lockout responses this way ("Account locked for …", "Account temporarily locked. Try again in …"). */
const LOCKOUT_PREFIX = /^Account (temporarily )?locked/;

@Component({
  imports: [ReactiveFormsModule, RouterLink, Brand],
  selector: 'app-login',
  templateUrl: './login.html',
})
/**
 * Login Component
 *
 * Renders the login form and delegates authentication to the Auth service.
 * Laid out like the redesign's "Log in" board; the auth page frame is shared with the register
 * page (styles/base.css). Messages follow the design's "States" board: a red box when the login
 * failed, amber when the account is locked, and a purple pointer for staff accounts.
 */
export class Login {
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly errorHeading = signal('Login failed');
  protected readonly errorKind = signal<LoginAlertKind>('err');
  protected readonly submitting = signal(false);
  protected readonly form: ReturnType<FormBuilder['group']>;
  // Tracks whether the password field currently shows plain text or is masked.
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
    const reason = route.snapshot.queryParamMap.get('reason');
    if (reason === INACTIVE_REASON) {
      this.showAccountInactive();
    }
    // Sent here by the inactivity timeout (BR-03, SessionTimeout) when the session ended.
    const ended = sessionEndMessage(reason);
    if (ended) {
      this.showAlert('info', ended.heading, ended.message);
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
      await this.auth.login(this.form.getRawValue() as { username: string; password: string });
      // Trading app is customer-only; staff accounts must use the staff app.
      if (this.auth.hasRole('TRADING_OPS')) {
        await this.auth.logout().catch(() => undefined);
        this.showAlert('info', 'Staff account', 'Please use the staff app for operations.');
        return;
      }
      await this.router.navigate(['/dashboard']);
    } catch (error) {
      if (isAccountInactive(error)) {
        this.showAccountInactive();
        return;
      }
      const serverMessage =
        error instanceof HttpErrorResponse &&
        typeof error.error === 'object' &&
        typeof error.error?.message === 'string'
          ? error.error.message
          : null;
      if (serverMessage && LOCKOUT_PREFIX.test(serverMessage)) {
        this.showAlert('warn', 'Account locked', serverMessage);
        return;
      }
      this.showAlert('err', 'Login failed', serverMessage ?? 'Invalid email or password.');
    } finally {
      this.submitting.set(false);
    }
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
