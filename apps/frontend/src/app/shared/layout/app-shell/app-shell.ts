import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../../../core/auth/auth';

/**
 * Signed-in layout from the LeUI mockup: sidebar navigation plus a content area that
 * hosts the dashboard and trade pages. Only pages that actually exist get nav items.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.html',
})
export class AppShell {
  protected readonly auth = inject(Auth);
}
