import { Component, inject } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { Auth } from '../../../core/auth/auth';

/**
 * Layout of every staff page: a header with logout button in the top right, and the
 * main content area below. Uses LeMarket branding to match the trading app's professional appearance.
 */
@Component({
  selector: 'staff-shell',
  imports: [RouterOutlet],
  templateUrl: './staff-shell.html',
  styleUrl: './staff-shell.css',
})
export class StaffShell {
  readonly auth = inject(Auth);
  private readonly router = inject(Router);

  async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/login']);
  }
}
