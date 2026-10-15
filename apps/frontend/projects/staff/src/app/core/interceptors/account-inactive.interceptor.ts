import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Auth } from '../auth/auth';
import { INACTIVE_REASON, isAccountInactive } from '../auth/account-inactive';

// True while the redirect is in flight, so a page whose requests all fail at once redirects once.
let redirecting = false;

/**
 * Sends the user back to the login page, with the reason shown, when the firm closes or suspends
 * their account mid-session: the backend then answers every request with 401 ACCOUNT_INACTIVE
 * (C6, LMKT-57). The session is already dead server-side, so only the local state is cleared.
 * A plain 401 (expired cookie) keeps today's handling. The error is always passed on, so callers
 * still see their request fail.
 */
export const accountInactiveInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isAccountInactive(error)) {
        auth.clearSession();
        // Before the first navigation (restoring a session at start-up) the auth guard sends the
        // user to the login page itself; a redirect started here would be overtaken by it.
        if (router.navigated && !redirecting && !router.url.startsWith('/login')) {
          redirecting = true;
          router.navigate(['/login'], { queryParams: { reason: INACTIVE_REASON } })
            .finally(() => { redirecting = false; });
        }
      }
      return throwError(() => error);
    }),
  );
};
