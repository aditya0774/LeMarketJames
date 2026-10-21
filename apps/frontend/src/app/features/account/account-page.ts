import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/auth/auth';
import { ACCOUNT_CLOSED_REASON, AccountClosure, AccountContents } from '../../core/account/account-closure.service';

/**
 * The client's account page, opened from the account menu. It holds "Close my account" (BR-03):
 * the page first shows what is left on the account, then asks for the password as a second step.
 * Closing is refused while stocks or open orders remain; cash may remain and is paid out by
 * operations, outside the platform.
 */
@Component({
  selector: 'app-account-page',
  imports: [CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './account-page.html',
  styleUrl: './account-page.css',
})
export class AccountPage implements OnInit {
  protected readonly auth = inject(Auth);
  private readonly closure = inject(AccountClosure);
  private readonly router = inject(Router);

  protected readonly contents = signal<AccountContents | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  /** True once the client has chosen to close and is asked for their password. */
  protected readonly confirming = signal(false);
  protected readonly closing = signal(false);
  protected readonly passwordError = signal('');
  protected readonly closeError = signal('');
  protected password = '';

  ngOnInit(): void {
    void this.load();
  }

  protected start(): void {
    this.closeError.set('');
    this.confirming.set(true);
  }

  protected cancel(): void {
    this.confirming.set(false);
    this.password = '';
    this.passwordError.set('');
  }

  protected async close(): Promise<void> {
    if (!this.password) {
      this.passwordError.set('Enter your password.');
      return;
    }
    this.passwordError.set('');
    this.closeError.set('');
    this.closing.set(true);
    try {
      await this.closure.close(this.password);
      // auth-service has cleared the cookie; only the local session is left to forget.
      this.auth.clearSession();
      await this.router.navigate(['/login'], { queryParams: { reason: ACCOUNT_CLOSED_REASON }, replaceUrl: true });
    } catch (error) {
      this.failed(error);
    } finally {
      this.closing.set(false);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.loadError.set('');
    try {
      this.contents.set(await this.closure.check());
    } catch {
      this.loadError.set('Unable to load your account. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private failed(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.closeError.set('Unable to close your account. Please try again.');
      return;
    }
    const passwordMessage = error.status === 400 ? error.error?.errors?.password : undefined;
    if (typeof passwordMessage === 'string') {
      this.passwordError.set(passwordMessage);
    } else if (error.status === 409) {
      // Something arrived since the page loaded (an order, a fill): show what is left now.
      this.closeError.set(error.error?.error ?? 'Your account cannot be closed yet.');
      this.cancel();
      void this.load();
    } else {
      this.closeError.set('Unable to close your account. Please try again.');
    }
  }
}
