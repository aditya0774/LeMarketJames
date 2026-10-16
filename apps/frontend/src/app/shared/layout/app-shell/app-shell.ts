import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../../../core/auth/auth';
import { Brand } from '../../components/brand/brand';
import { AccountMenu } from '../account-menu/account-menu';

/**
 * Signed-in layout: the header with the section tabs and the account menu, above the page
 * content. The trading app is customer-only (LMKT-145), so the tabs link nothing else; staff use
 * the staff app. Sign-out lives in the account menu, so every signed-in page has it.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Brand, AccountMenu],
  templateUrl: './app-shell.html',
})
export class AppShell {
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected async logout(): Promise<void> {
    try {
      await this.auth.logout();
    } finally {
      await this.router.navigate(['/']);
    }
  }
}
