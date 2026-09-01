import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  // This object stores exactly the data required by the registration acceptance criteria.
  // Each field maps to one business requirement from PB-01.
  registerData = {
    fullName: '',
    streetAddress: '',
    apartment: '',
    city: '',
    state: '',
    zipCode: '',
    country: '',
    ssn: '',
    initialDeposit: '',
    investmentExperience: 'beginner',
    dateOfBirth: '',
    email: '',
    phoneNumber: '',
    password: ''
  };

  // This object keeps a separate error message for each form field.
  // That way, the user sees exactly which field is missing.
  validationErrors: Record<string, string> = {};

  // This array lists each required field and the label shown to the user.
  // It keeps the validation rules in one place and makes the code easier to maintain.
  requiredFields = [
    { key: 'fullName', label: 'Name' },
    { key: 'streetAddress', label: 'Street address' },
    { key: 'city', label: 'City' },
    { key: 'state', label: 'State' },
    { key: 'zipCode', label: 'ZIP code' },
    { key: 'country', label: 'Country' },
    { key: 'ssn', label: 'SSN' },
    { key: 'initialDeposit', label: 'Initial deposit amount' },
    { key: 'dateOfBirth', label: 'Date of birth' },
    { key: 'email', label: 'Email' },
    { key: 'phoneNumber', label: 'Phone' },
    { key: 'password', label: 'Password' }
  ];

  // This method clears the error for a single field when the user starts editing it.
  clearFieldError(fieldName: string) {
    this.validationErrors[fieldName] = '';
  }

  // This method validates one field and stores a field-specific message if it is empty.
  validateRequiredField(fieldName: string, fieldValue: string, label: string) {
    const value = String(fieldValue ?? '').trim();

    if (value === '') {
      this.validationErrors[fieldName] = `${label} is required.`;
      return false;
    }

    this.validationErrors[fieldName] = '';
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

  // This method runs when the registration form is submitted.
  // It checks every required field and stops submission if any are missing.
  onSubmit() {
    let isFormValid = true;

    this.requiredFields.forEach(({ key, label }) => {
      const fieldValue = this.registerData[key as keyof typeof this.registerData];
      const isValid = this.validateRequiredField(key, String(fieldValue), label);

      if (!isValid) {
        isFormValid = false;
      }
    });

    // Make sure the phone number is exactly 10 digits before accepting it.
    const phoneDigits = this.registerData.phoneNumber.replace(/\D/g, '');
    if (phoneDigits.length !== 10) {
      this.validationErrors['phoneNumber'] = 'Phone number must be 10 digits.';
      isFormValid = false;
    }

    // Make sure the SSN is exactly 9 digits before accepting it in the format XXX-XX-XXXX.
    const ssnDigits = this.registerData.ssn.replace(/\D/g, '');
    if (ssnDigits.length !== 9) {
      this.validationErrors['ssn'] = 'SSN must be 9 digits in the format xxx-xx-xxxx.';
      isFormValid = false;
    }

    if (!isFormValid) {
      return;
    }

    // Clear all messages before submitting successfully.
    this.validationErrors = {};
    console.log('Registration data:', this.registerData);
  }
}