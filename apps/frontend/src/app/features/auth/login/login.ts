import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../../core/auth/auth';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
/**
 * Login Component
 *
 * Renders the login form and delegates authentication to the Auth service.
 * Uses the shared LeMarket auth styles (see styles.css and the LeUI mockup),
 * matching the register page, including its password show/hide toggle.
 */
export class Login {
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly form: ReturnType<FormBuilder['group']>;
  // Tracks whether the password field currently shows plain text or is masked.
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
      await this.auth.login(this.form.getRawValue() as { username: string; password: string });
      await this.router.navigate(['/']);
    } catch (error) {
      const serverMessage =
        error instanceof HttpErrorResponse &&
        typeof error.error === 'object' &&
        typeof error.error?.message === 'string'
          ? error.error.message
          : null;
      this.errorMessage.set(serverMessage ?? 'Invalid username or password.');
    } finally {
      this.submitting.set(false);
    }
  }

  protected togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }
}