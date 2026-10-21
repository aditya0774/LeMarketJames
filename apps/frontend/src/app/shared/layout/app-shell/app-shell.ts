import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../../../core/auth/auth';
import { Brand } from '../../components/brand/brand';
import { AccountMenu, SessionTimeout } from '@lemarket/shared-ui';

/**
 * Signed-in layout: the header with the section tabs and the account menu, above the page
 * content. The trading app is customer-only (LMKT-145), so the tabs link nothing else; staff use
 * the staff app. Sign-out and the client's account page (where they can close their account,
 * BR-03) live in the account menu, so every signed-in page has them, and so does
 * the inactivity timeout (BR-03), which signs an idle session out.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Brand, AccountMenu, SessionTimeout],
  templateUrl: './app-shell.html',
})
export class AppShell {
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected async logout(): Promise<void> {
    try {
      await this.auth.logout();
    } finally {
      await this.router.navigate(['/'], { replaceUrl: true });
    }
  }
}
