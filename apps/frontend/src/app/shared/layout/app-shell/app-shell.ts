import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../../../core/auth/auth';

/**
 * Signed-in layout: a left navigation rail (bottom tab bar on small screens) beside the page
 * content. The trading app is customer-only (LMKT-145), so the rail links nothing else; staff
 * use the staff app. Sign-out lives here so every signed-in page has it.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.html',
})
export class AppShell {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected async logout(): Promise<void> {
    try {
      await this.auth.logout();
    } finally {
      await this.router.navigate(['/'], { replaceUrl: true });
    }
  }
}
