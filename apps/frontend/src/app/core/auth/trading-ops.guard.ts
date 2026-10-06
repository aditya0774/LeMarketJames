import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

/** UI access check; the API independently enforces TRADING_OPS (C7). */
export const tradingOpsGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.currentUser()) return router.createUrlTree(['/login']);
  return auth.hasRole('TRADING_OPS') || router.createUrlTree(['/access-denied']);
};
