import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { z } from 'zod';
import { registerSchema, RegisterFormData } from './register.schema';

@Component({
  imports: [CommonModule, FormsModule, RouterLink],
  selector: 'app-register',
  standalone: true,
  templateUrl: './register.html',
  styleUrl: './register.css',
})
/**
 * Register Component
 * 
 * Handles user registration with comprehensive form validation and error handling.
 * All validation rules are defined in register.schema.ts using the Zod schema validation library.
 * 
 * Form Data Structure:
 * - Name fields: firstName (required), middleName (optional), lastName (required)
 * - Address fields: streetAddress, apartment (optional), city, state, zipCode
 * - Identity fields: ssn, dateOfBirth
 * - Financial fields: initialDeposit (minimum $5,000), investmentExperience, employmentStatus
 * - Contact fields: email, phoneNumber
 * - Security fields: password, confirmPassword
 * 
 * Validation Strategy:
 * - Field-level validation occurs on blur and input (error cleared on input)
 * - Schema-level validation occurs on form submission
 * - Cross-field rules (password match, experience level requirements) are checked via superRefine()
 * 
 * Form Binding:
 * - Two-way binding via [(ngModel)] syncs form inputs to registerData object
 * - Input formatting happens in real-time (phone, SSN, ZIP, deposit)
 * - All formatted values are tracked by the component for display
 */
export class Register {
  // Error message and submitting state as signals for reactive template updates
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly submitting = signal(false);

  // This object stores exactly the data required by the registration form.
  // Its shape matches RegisterFormData (inferred from register.schema.ts), so the form
  // fields and the validation rules defined in the schema can never fall out of sync.
  // Names are now split: firstName (required), middleName (optional), lastName (required)
  // This allows for better international name support and data accuracy.
  registerData: RegisterFormData = {
    firstName: '',
    middleName: '',
    lastName: '',
    streetAddress: '',
    apartment: '',
    city: '',
    state: '',
    zipCode: '',
    ssn: '',
    initialDeposit: '',
    investmentExperience: 'beginner',
    employmentStatus: 'employed',
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

  /**
   * Checks if the user's investment experience level can be set to 'experienced'.
   * 
   * @returns {boolean} True if initial deposit is $100,000 or more, false otherwise
   * 
   * Note: This method is used to conditionally enable/disable the 'experienced' option
   * in the investment experience dropdown. Users must meet the minimum deposit requirement.
   */
  isExperiencedAllowed(): boolean {
    return Number(this.registerData.initialDeposit) >= 100000;
  }

  /**
   * Handles input changes to the initial deposit field.
   * 
   * Responsibilities:
   * - Clears any existing validation error for this field
   * - Downgrades investment experience from 'experienced' to 'beginner' if the deposit
   *   amount drops below the $100,000 threshold
   * 
   * This prevents users from having an invalid state where they've selected 'experienced'
   * but don't have sufficient funds.
   */
  onInitialDepositInput() {
    this.clearFieldError('initialDeposit');

    if (!this.isExperiencedAllowed() && this.registerData.investmentExperience === 'experienced') {
      this.registerData.investmentExperience = 'beginner';
    }
  }

  /**
   * Toggles the password field visibility between masked and plain text.
   * 
   * Used by the "Show/Hide password" button to allow users to verify their password
   * before submission without accidentally typing characters they didn't intend.
   */
  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  /**
   * Clears the validation error message for a specific field.
   * 
   * Called when the user starts editing a field (on input event), providing immediate
   * feedback that their change is being recognized.
   * 
   * @param {string} fieldName - The name of the field to clear the error for
   */
  clearFieldError(fieldName: string) {
    this.validationErrors[fieldName] = '';
  }

  /**
   * Validates a single form field against the complete Zod schema.
   * 
   * Why validate the entire schema?
   * - Some validation rules depend on multiple fields (e.g., password match, experience level requirements)
   * - Zod's error path tracking lets us extract issues for a specific field
   * - This ensures consistent validation logic between field-level and form-level checks
   * 
   * @param {keyof RegisterFormData} fieldName - The field to check and report errors for
   * @returns {boolean} True if validation passes, false if there's an error
   * 
   * Logic:
   * 1. Parse entire form against schema
   * 2. If successful, clear any error for this field and return true
   * 3. If failed, find the first error issue for this field and store its message
   * 4. If this field has no errors (but others do), clear the error for this field
   */
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

  /**
   * Formats a phone number string into (XXX) XXX-XXXX format.
   * 
   * @param {string} value - Raw phone number input (may contain non-digit characters)
   * @returns {string} Formatted phone number, or partial format if user is still typing
   * 
   * Behavior:
   * - Strips all non-digit characters
   * - Limits to 10 digits (US phone numbers)
   * - Progressively formats as user types:
   *   - 1-3 digits: "123"
   *   - 4-6 digits: "(123) 456"
   *   - 7-10 digits: "(123) 456-7890"
   */
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

  /**
   * Formats a Social Security Number into XXX-XX-XXXX format.
   * 
   * @param {string} value - Raw SSN input (may contain non-digit characters)
   * @returns {string} Formatted SSN, or partial format if user is still typing
   * 
   * Behavior:
   * - Strips all non-digit characters
   * - Limits to 9 digits (US SSN length)
   * - Progressively formats as user types:
   *   - 1-3 digits: "123"
   *   - 4-5 digits: "123-45"
   *   - 6-9 digits: "123-45-6789"
   */
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

  /**
   * Formats a ZIP code to exactly 5 digits.
   * 
   * @param {string} value - Raw ZIP code input (may contain non-digit characters)
   * @returns {string} ZIP code with only digits, capped at 5 characters
   * 
   * US ZIP codes are always 5 digits (basic format; ZIP+4 not supported here)
   */
  formatZipCode(value: string): string {
    return value.replace(/\D/g, '').slice(0, 5);
  }

  /**
   * Handles input changes to the phone number field.
   * 
   * Event handler for the input event on the phone number field.
   * 
   * Responsibilities:
   * - Formats the raw input using formatPhoneNumber()
   * - Updates both the DOM input and the component's registerData model
   * - Clears any validation error for this field
   * 
   * This ensures the displayed value, stored value, and validation state stay in sync.
   * 
   * @param {Event} event - The input event containing the phone number field
   */
  onPhoneInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const formattedValue = this.formatPhoneNumber(input.value);

    input.value = formattedValue;
    this.registerData.phoneNumber = formattedValue;

    // Remove the field error as the user fixes the value.
    this.clearFieldError('phoneNumber');
  }

  /**
   * Handles input changes to the SSN field.
   * 
   * Event handler for the input event on the SSN field.
   * 
   * Responsibilities:
   * - Formats the raw input using formatSsn()
   * - Updates both the DOM input and the component's registerData model
   * - Clears any validation error for this field
   * 
   * This ensures the displayed value, stored value, and validation state stay in sync.
   * 
   * @param {Event} event - The input event containing the SSN field
   */
  onSsnInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const formattedValue = this.formatSsn(input.value);

    input.value = formattedValue;
    this.registerData.ssn = formattedValue;

    // Remove the field error as the user fixes the value.
    this.clearFieldError('ssn');
  }

  /**
   * Handles input changes to the ZIP code field.
   * 
   * Event handler for the input event on the ZIP code field.
   * 
   * Responsibilities:
   * - Formats the raw input using formatZipCode()
   * - Updates both the DOM input and the component's registerData model
   * - Clears any validation error for this field
   * 
   * This ensures the displayed value, stored value, and validation state stay in sync.
   * 
   * @param {Event} event - The input event containing the ZIP code field
   */
  onZipCodeInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const formattedValue = this.formatZipCode(input.value);

    input.value = formattedValue;
    this.registerData.zipCode = formattedValue;

    // Remove the field error as the user fixes the value.
    this.clearFieldError('zipCode');
  }


  /**
   * Handles form submission.
   * 
   * Called when the user clicks the "Create account" button or submits the form.
   * 
   * Process:
   * 1. Validates the entire form against the Zod schema in one pass
   * 2. If validation fails:
   *    - Rebuilds the error map from scratch
   *    - Keeps only the first error message per field (fields can fail multiple rules)
   *    - Falls back 'experienced' to 'beginner' if the investment experience field failed
   *    - Stops execution; form is not submitted
   * 3. If validation succeeds:
   *    - Clears all error messages
   *    - Sets the general error message to null
   *    - Sets submitting state (disables submit button)
   *    - Logs the registration data to the console (ready for API integration)
   *    - Would typically make an API call to submit the registration
   * 
   * Note: Currently logs to console; integrate with actual registration API endpoint
   */
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
    this.errorMessage.set(null);
    this.submitting.set(true);
    
    try {
      console.log('Registration data:', result.data);
      // TODO: Integrate with actual registration API endpoint
      // After successful API call, navigate to login:
      // await this.router.navigate(['/login']);
    } catch (error) {
      this.errorMessage.set('Registration failed. Please try again.');
    } finally {
      this.submitting.set(false);
    }
  }
}
