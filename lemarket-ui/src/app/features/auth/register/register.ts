import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { z } from 'zod';
import { registerSchema, RegisterFormData } from './register.schema';

@Component({
  imports: [],
  selector: 'app-register',
  styleUrl: './register.css',
  templateUrl: './register.html',
})
export class Register {
  // This object stores exactly the data required by the registration acceptance criteria.
  // Its shape matches RegisterFormData (inferred from register.schema.ts), so the form
  // fields and the validation rules defined in the schema can never fall out of sync.
  registerData: RegisterFormData = {
    fullName: '',
    streetAddress: '',
    apartment: '',
    city: '',
    state: '',
    zipCode: '',
    ssn: '',
    initialDeposit: '',
    investmentExperience: 'beginner',
    dateOfBirth: '',
    email: '',
    phoneNumber: '',
    password: '',
    confirmPassword: ''
  };

  // This object keeps a separate error message for each form field.
  // That way, the user sees exactly which field is missing or invalid.
  validationErrors: Record<string, string> = {};
  // Tracks whether the password field currently shows plain text or is masked.
  showPassword = false;

  // This method returns true only when the initial deposit is $100,000 or more.
  // 'Experienced' is only selectable once this threshold is met.
  isExperiencedAllowed(): boolean {
    return Number(this.registerData.initialDeposit) >= 100000;
  }

  // This method is called every time the user types into the initial deposit field.
  // It clears the field error and downgrades the experience level if it's no longer allowed.
  onInitialDepositInput() {
    this.clearFieldError('initialDeposit');

    if (!this.isExperiencedAllowed() && this.registerData.investmentExperience === 'experienced') {
      this.registerData.investmentExperience = 'beginner';
    }
  }

  // This method toggles the password field between masked and plain text.
  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  // This method clears the error for a single field when the user starts editing it.
  clearFieldError(fieldName: string) {
    this.validationErrors[fieldName] = '';
  }

  // This method re-checks the whole form against the Zod schema (see register.schema.ts) and
  // surfaces only the first error found for the one field passed in. The whole schema has to be
  // parsed (not just this field in isolation) because some rules span multiple fields — e.g. the
  // confirm password match and the "experienced needs $100k+ deposit" rule.
  validateField(fieldName: keyof RegisterFormData): boolean {
    const result = registerSchema.safeParse(this.registerData);

    if (result.success) {
      this.clearFieldError(fieldName);
      return true;
    }

    const fieldIssue = result.error.issues.find((issue) => issue.path[0] === fieldName);

    if (fieldIssue) {
      this.validationErrors[fieldName] = fieldIssue.message;
      return false;
    }

    // This field itself is fine, even though other fields in the form are still invalid.
    this.clearFieldError(fieldName);
    return true;
  }

  // This method converts a raw phone number into the format (XXX) XXX-XXXX.
  // It strips out any non-digit characters so the user cannot type letters or symbols.
  formatPhoneNumber(value: string): string {
    const digitsOnly = value.replace(/\D/g, '').slice(0, 10);

    if (digitsOnly.length <= 3) {
      return digitsOnly;
    }

    if (digitsOnly.length <= 6) {
      return `(${digitsOnly.slice(0, 3)}) ${digitsOnly.slice(3)}`;
    }

    return `(${digitsOnly.slice(0, 3)}) ${digitsOnly.slice(3, 6)}-${digitsOnly.slice(6)}`;
  }

  // This method converts a raw SSN into the format XXX-XX-XXXX.
  // It strips out any non-digit characters so the user cannot type letters or symbols.
  formatSsn(value: string): string {
    const digitsOnly = value.replace(/\D/g, '').slice(0, 9);

    if (digitsOnly.length <= 3) {
      return digitsOnly;
    }

    if (digitsOnly.length <= 5) {
      return `${digitsOnly.slice(0, 3)}-${digitsOnly.slice(3)}`;
    }

    return `${digitsOnly.slice(0, 3)}-${digitsOnly.slice(3, 5)}-${digitsOnly.slice(5)}`;
  }

  // This method strips non-digit characters from the ZIP code and caps it at 5 digits.
  formatZipCode(value: string): string {
    return value.replace(/\D/g, '').slice(0, 5);
  }

  // This method is called every time the user types into the phone field.
  // It sanitizes the input and updates the model with the formatted value.
  onPhoneInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const formattedValue = this.formatPhoneNumber(input.value);

    input.value = formattedValue;
    this.registerData.phoneNumber = formattedValue;

    // Remove the field error as the user fixes the value.
    this.clearFieldError('phoneNumber');
  }

  // This method is called every time the user types into the SSN field.
  // It sanitizes the input and updates the model with the formatted value.
  onSsnInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const formattedValue = this.formatSsn(input.value);

    input.value = formattedValue;
    this.registerData.ssn = formattedValue;

    // Remove the field error as the user fixes the value.
    this.clearFieldError('ssn');
  }

  // This method is called every time the user types into the ZIP code field.
  // It sanitizes the input and updates the model with the formatted value.
  onZipCodeInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const formattedValue = this.formatZipCode(input.value);

    input.value = formattedValue;
    this.registerData.zipCode = formattedValue;

    // Remove the field error as the user fixes the value.
    this.clearFieldError('zipCode');
  }

  // This method runs when the registration form is submitted.
  // It validates the entire form against the Zod schema in one pass and stops
  // submission if any field is missing or invalid.
  onSubmit() {
    const result = registerSchema.safeParse(this.registerData);

    if (!result.success) {
      // Rebuild the error map from scratch so it always reflects the latest data.
      this.validationErrors = {};

      // Keep only the first message per field, since a field can fail more than one rule.
      result.error.issues.forEach((issue: z.ZodIssue) => {
        const fieldName = String(issue.path[0]);

        if (!this.validationErrors[fieldName]) {
          this.validationErrors[fieldName] = issue.message;
        }
      });

      // If 'experienced' was rejected, fall back to 'beginner' so the form stays consistent.
      if (this.validationErrors['investmentExperience']) {
        this.registerData.investmentExperience = 'beginner';
      }

      return;
    }

    // Clear all messages before submitting successfully.
    this.validationErrors = {};
    console.log('Registration data:', result.data);
  }
}
