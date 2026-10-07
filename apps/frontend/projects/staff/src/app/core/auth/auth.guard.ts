import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

/** Keeps signed-out visitors out of the staff app. Safe to read the signal synchronously. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  return auth.currentUser() ? true : inject(Router).createUrlTree(['/login']);
};
