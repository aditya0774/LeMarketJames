import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  imports: [FormsModule],
  selector: 'app-register',
  styleUrl: './register.css',
  templateUrl: './register.html',
})
export class Register {
  private readonly http = inject(HttpClient);

  form = {
    firstName: '',
    lastName: '',
    address: '',
    email: '',
    phoneNumber: '',
    password: ''
  };
  message = '';
  error = '';

  register(): void {
    this.message = '';
    this.error = '';

    this.http.post('http://localhost:8081/api/auth/register', this.form).subscribe({
      next: () => {
        this.message = 'Registration successful.';
        this.form = { firstName: '', lastName: '', address: '', email: '', phoneNumber: '', password: '' };
      },
      error: (response) => {
        this.error = response.error?.message ?? 'Registration failed. Please try again.';
      }
    });
  }
}
