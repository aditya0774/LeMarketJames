import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Shown on a signed-in page once the API answers 401, i.e. the session cookie has expired.
 * Wording from the redesign's "States & messages" board.
 */
@Component({
  selector: 'app-session-expired',
  imports: [RouterLink],
  template: `
    <div class="alert warn session-expired" role="alert">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </svg>
      <div>
        <b>You've been logged out</b>
        Your session has expired. For your security, sessions end after a while. Log in again to pick up
        where you left off.
      </div>
      <a class="btn btn-primary btn-sm" routerLink="/login">Log in again</a>
    </div>
  `,
  styles: `
    .session-expired {
      align-items: center;
    }
    .session-expired div {
      flex: 1;
    }
  `,
})
export class SessionExpired {}
