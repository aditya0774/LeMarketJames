import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, ValidatorFn, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../../core/auth/auth';

// Requires an exact number of digits once non-digit characters are stripped (used for phone/SSN masks).
function exactDigitCount(count: number): ValidatorFn {
  return (control) => {
    if (!control.value) {
      return null;
    }
    const digits = String(control.value).replace(/\D/g, '');
    return digits.length === count ? null : { digitCount: true };
  };
}

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-register',
  standalone: true,
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly form: ReturnType<FormBuilder['group']>;

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: Auth,
    private readonly router: Router,
  ) {
    this.form = this.fb.group({
      username: ['', Validators.required],
      fullName: ['', Validators.required],
      streetAddress: ['', Validators.required],
      apartment: [''],
      city: ['', Validators.required],
      state: ['', Validators.required],
      zipCode: ['', Validators.required],
      country: ['', Validators.required],
      ssn: ['', [Validators.required, exactDigitCount(9)]],
      initialDeposit: ['', [Validators.required, Validators.min(0)]],
      investmentExperience: ['beginner', Validators.required],
      dateOfBirth: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phoneNumber: ['', [Validators.required, exactDigitCount(10)]],
      password: ['', [Validators.required, Validators.minLength(8)]],
    });
  }

  // Reformats the SSN field as-you-type into XXX-XX-XXXX.
  onSsnInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 9);
    const formatted = this.formatSsn(digits);
    input.value = formatted;
    this.form.get('ssn')?.setValue(formatted);
  }

  // Reformats the phone field as-you-type into (XXX) XXX-XXXX.
  onPhoneInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 10);
    const formatted = this.formatPhoneNumber(digits);
    input.value = formatted;
    this.form.get('phoneNumber')?.setValue(formatted);
  }

  private formatSsn(digits: string): string {
    if (digits.length <= 3) return digits;
    if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
  }

  private formatPhoneNumber(digits: string): string {
    if (digits.length <= 3) return digits;
    if (digits.length <= 6) return `(${digits.slice(0, 3)}) ${digits.slice(3)}`;
    return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`;
  }

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);
    try {
      const raw = this.form.getRawValue();
      await this.auth.register({
        ...raw,
        initialDeposit: Number(raw.initialDeposit),
      } as Parameters<Auth['register']>[0]);
      await this.router.navigate(['/login']);
    } catch {
      this.errorMessage.set('Registration failed. The username may already be taken.');
    } finally {
      this.submitting.set(false);
    }
  }
}
