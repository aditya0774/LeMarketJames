import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionEndReason, SessionKeeper, SessionLease } from '@lemarket/shared-ui';
import { Auth } from './auth';

/**
 * This app's side of the inactivity timeout (BR-03, @lemarket/shared-ui SessionTimeout): it
 * extends the session through auth-service and, when the session ends, signs out and shows the
 * login page with the reason. Provided as SESSION_KEEPER in app.config.ts.
 */
@Injectable({ providedIn: 'root' })
export class AuthSessionKeeper implements SessionKeeper {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  current(): SessionLease | null {
    return this.auth.sessionLease();
  }

  extend(): Promise<SessionLease> {
    return this.auth.refresh();
  }

  async end(reason: SessionEndReason): Promise<void> {
    // The token has usually run out already, and signing out only clears the cookie. If it hasn't
    // (a refresh in flight, a clock that is off), signing out makes the server refuse it too.
    await this.auth.logout().catch(() => this.auth.clearSession());
    await this.router.navigate(['/login'], { queryParams: { reason }, replaceUrl: true });
  }
}
