import { Component, DestroyRef, ElementRef, effect, inject, viewChild } from '@angular/core';
import { IdleSession } from './idle-session';

/**
 * The inactivity timeout's warning (BR-03): placed once in each app's signed-in layout, it watches
 * for activity through IdleSession and, 30 seconds before an idle session ends, asks whether the
 * user is still there. "Stay signed in" extends the session; "Sign out" or no answer ends it, and
 * the app's SESSION_KEEPER shows the login page with the reason.
 *
 * An alert dialog, so screen readers announce it as soon as it opens, and focus moves to "Stay
 * signed in" so a key press answers it. The countdown is not a live region: re-reading it every
 * second would drown out everything else.
 */
@Component({
  selector: 'lm-session-timeout',
  providers: [IdleSession],
  template: `
    @if (idle.secondsLeft(); as seconds) {
      <div class="session-timeout-scrim">
        <div
          class="session-timeout"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="session-timeout-title"
          aria-describedby="session-timeout-text"
        >
          <h2 id="session-timeout-title">Are you still there?</h2>
          <p id="session-timeout-text">
            For your security you will be signed out in
            <b class="session-timeout-count">{{ seconds }} {{ seconds === 1 ? 'second' : 'seconds' }}</b>.
          </p>
          <div class="session-timeout-actions">
            <button type="button" class="btn btn-outline" (click)="idle.signOutNow()">Sign out</button>
            <button #stay type="button" class="btn btn-primary" (click)="idle.staySignedIn()">Stay signed in</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class SessionTimeout {
  protected readonly idle = inject(IdleSession);
  private readonly stay = viewChild<ElementRef<HTMLButtonElement>>('stay');

  constructor() {
    this.idle.start();
    inject(DestroyRef).onDestroy(() => this.idle.stop());
    // The button exists only while the warning shows; focus it when it appears.
    effect(() => this.stay()?.nativeElement.focus());
  }
}
