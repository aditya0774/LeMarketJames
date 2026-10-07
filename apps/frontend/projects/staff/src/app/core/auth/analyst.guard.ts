import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from '../../../src/app/core/auth/auth';

/**
 * UI access check for ANALYST role.
 * The API (reporting-service) independently enforces ANALYST role (C7).
 * This guard redirects to login if not authenticated, or to /access-denied if not ANALYST.
 */
export const analystGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.currentUser()) return router.createUrlTree(['/login']);
  return auth.hasRole('ANALYST') || router.createUrlTree(['/access-denied']);
};
