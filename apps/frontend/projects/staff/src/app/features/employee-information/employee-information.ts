import { Component, computed, inject } from '@angular/core';
import { Auth } from '../../core/auth/auth';
import { roleLabel } from '../../core/auth/role-label';

/**
 * Employee information (LMKT-144): who the signed-in staff member is, from their own session only.
 * Read-only by design: staff accounts are made by engineers, so there is nothing to edit, no
 * password change and no staff management here. A detail the session doesn't carry is left out
 * rather than shown empty.
 */
@Component({
  selector: 'staff-employee-information',
  templateUrl: './employee-information.html',
  styleUrl: './employee-information.css',
})
export class EmployeeInformation {
  protected readonly auth = inject(Auth);
  protected readonly role = computed(() => roleLabel(this.auth.roles()));
}
