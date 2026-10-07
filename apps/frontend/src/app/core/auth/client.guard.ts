import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

/** UI access check for customer-facing pages; requires CLIENT role. The API independently enforces CLIENT (C7). */
export const clientGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.currentUser()) return router.createUrlTree(['/login']);
  return auth.hasRole('CLIENT') || router.createUrlTree(['/access-denied']);
};
