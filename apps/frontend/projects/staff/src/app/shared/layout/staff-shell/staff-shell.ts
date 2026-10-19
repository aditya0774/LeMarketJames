import { Component, computed, inject } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { Auth, Role } from '../../../core/auth/auth';
import { Brand } from '../../components/brand/brand';
import { AccountMenu } from '@lemarket/shared-ui';

/** How each staff role is named in the header and the account menu. */
const ROLE_LABELS: Partial<Record<Role, string>> = {
  TRADING_OPS: 'Trading Operations',
  ANALYST: 'Analyst',
};

/**
 * Layout of every staff page, from the redesign's staff boards: a header with the lemarket brand,
 * the "Courtside View" badge, the signed-in role and the account menu (theme and log out), above
 * the page content.
 */
@Component({
  selector: 'staff-shell',
  imports: [RouterOutlet, Brand, AccountMenu],
  templateUrl: './staff-shell.html',
  styleUrl: './staff-shell.css',
})
export class StaffShell {
  readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly roleLabel = computed(
    () => this.auth.roles().map((role) => ROLE_LABELS[role]).find((label) => !!label) ?? 'Staff',
  );

  async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/login']);
  }
}
