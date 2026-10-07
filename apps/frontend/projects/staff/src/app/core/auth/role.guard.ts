import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth, Role } from './auth';
import { ACCESS_DENIED } from './staff-home';

/**
 * Lets a page be opened by any of the given roles: requiresRole('ANALYST'), or
 * requiresRole('TRADING_OPS', 'COMPLIANCE') for a page two roles share. A signed-out visitor is
 * sent to the staff login; a signed-in account with none of the roles sees Access denied.
 *
 * This only decides what the app shows. The service behind the page enforces the role itself
 * (C7), so a page guarded here still needs its hasRole rule on the server.
 */
export function requiresRole(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(Auth);
    const router = inject(Router);
    if (!auth.currentUser()) return router.createUrlTree(['/login']);
    return roles.some((role) => auth.hasRole(role)) || router.createUrlTree([ACCESS_DENIED]);
  };
}
