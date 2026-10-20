import { Component, computed, inject } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { Auth } from '../../../core/auth/auth';
import { roleLabel } from '../../../core/auth/role-label';
import { EMPLOYEE_INFORMATION } from '../../../features/employee-information/employee-information.route';
import { Brand } from '../../components/brand/brand';
import { AccountMenu } from '@lemarket/shared-ui';

/**
 * Layout of every staff page, from the redesign's staff boards: a header with the lemarket brand,
 * the "Courtside View" badge, the signed-in role and the account menu (Employee information, theme
 * and log out), above the page content. Being in the header of every page is what makes Employee
 * information reachable from both the Trading Ops and the Analyst dashboards (LMKT-144).
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

  protected readonly roleLabel = computed(() => roleLabel(this.auth.roles()));
  protected readonly employeeInformation = EMPLOYEE_INFORMATION;

  async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/login'], { replaceUrl: true });
  }
}
