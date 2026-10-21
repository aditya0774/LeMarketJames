import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { SESSION_EXPIRED_REASON, SESSION_KEEPER, SESSION_TIMEOUT_REASON, SessionLease } from './session-keeper';

/** Where tabs of one app share when the session ends, so an idle tab doesn't end a busy tab's session. */
export const SESSION_EXPIRY_STORAGE_KEY = 'lemarket-session-expiry';

/** Warn this long before the session ends (BR-03: 30 seconds), or half the timeout if that is shorter. */
const WARNING_MS = 30_000;
/** Extend at most this often while the user is active, or a third of the timeout if that is shorter. */
const EXTEND_EVERY_MS = 20_000;
/** How often the deadline is checked; also how often the countdown in the warning moves. */
const TICK_MS = 1_000;
/** The input that counts as someone using the app. Background polling and SSE never do. */
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'wheel', 'touchstart'] as const;

interface SharedExpiry {
  expiresAt: number;
  capped: boolean;
}

/**
 * Ends a signed-in session nobody is using (BR-03). The session token lasts only the inactivity
 * timeout, so the server ends the session anyway; this keeps it going while the user is active,
 * warns 30 seconds before the end, and takes the user to the login page when it runs out instead
 * of leaving a page whose requests all fail.
 *
 * - Activity (mouse, keys, touch, coming back to the tab) is only noted when it happens; the
 *   session is extended at most every 20 seconds, so moving the mouse doesn't flood the server.
 * - Once the warning shows, only "Stay signed in" extends the session: the user has to answer it.
 * - Every tab of the app shares the end time through localStorage, so a busy tab keeps an idle
 *   one signed in. Without storage each tab times out on its own.
 * - Times come from Date.now(), so tests (and Playwright's clock) can move time instead of waiting.
 *
 * Provided by SessionTimeout, so it lives exactly as long as the signed-in layout.
 */
@Injectable()
export class IdleSession {
  private readonly keeper = inject(SESSION_KEEPER);
  private readonly document = inject(DOCUMENT);

  /** Seconds until sign-out while the warning shows; null otherwise. */
  readonly secondsLeft = signal<number | null>(null);

  private expiresAt: number | null = null;
  private capped = false;
  private timeoutMs = 0;
  private lastActivity = 0;
  private lastExtendAttempt = 0;
  private extending = false;
  private running = false;
  private ticker: ReturnType<typeof setInterval> | undefined;

  private readonly onActivity = (): void => {
    this.lastActivity = Date.now();
  };

  private readonly onVisibility = (): void => {
    if (this.document.visibilityState === 'visible') {
      // Background tabs run timers rarely, so the deadline may have passed while hidden.
      this.tick();
      this.onActivity();
    }
  };

  /** Starts watching. Opening the signed-in layout (sign-in or a page load) counts as activity. */
  start(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    const known = this.keeper.current();
    if (known) {
      this.accept(known, false);
    }
    for (const type of ACTIVITY_EVENTS) {
      this.document.addEventListener(type, this.onActivity, { capture: true, passive: true });
    }
    this.document.addEventListener('visibilitychange', this.onVisibility);
    this.ticker = setInterval(() => this.tick(), TICK_MS);
    void this.extend();
  }

  /** Stops watching; nothing is ended. */
  stop(): void {
    this.running = false;
    clearInterval(this.ticker);
    for (const type of ACTIVITY_EVENTS) {
      this.document.removeEventListener(type, this.onActivity, { capture: true });
    }
    this.document.removeEventListener('visibilitychange', this.onVisibility);
    this.secondsLeft.set(null);
  }

  /** The warning's "Stay signed in". */
  async staySignedIn(): Promise<void> {
    await this.extend();
    this.tick();
  }

  /** The warning's "Sign out": ends the session now, as the timeout would. */
  async signOutNow(): Promise<void> {
    await this.end();
  }

  private tick(): void {
    if (!this.running || this.expiresAt === null) {
      return;
    }
    this.adoptShared();
    const now = Date.now();
    const left = this.expiresAt - now;
    if (left <= 0) {
      void this.end();
      return;
    }
    if (left <= Math.min(WARNING_MS, this.timeoutMs / 2)) {
      this.secondsLeft.set(Math.ceil(left / 1000));
      return;
    }
    this.secondsLeft.set(null);
    const extendEvery = Math.min(EXTEND_EVERY_MS, this.timeoutMs / 3);
    if (this.lastActivity > this.lastExtendAttempt && now - this.lastExtendAttempt >= extendEvery) {
      void this.extend();
    }
  }

  private async extend(): Promise<void> {
    if (this.extending || !this.running) {
      return;
    }
    this.extending = true;
    // Counted from the attempt, so a failing server isn't retried every second.
    this.lastExtendAttempt = Date.now();
    try {
      this.accept(await this.keeper.extend(), true);
    } catch {
      // Left to the deadline: the session ends when it was going to, with the usual message.
    } finally {
      this.extending = false;
    }
  }

  /**
   * Takes a lease as the new end time. Only an extension can tell that the session hit its time
   * limit: it then lasts less than a full timeout. A page-load lease is just partly used.
   */
  private accept(lease: SessionLease, extended: boolean): void {
    this.timeoutMs = lease.inactivityTimeoutSeconds * 1000;
    this.expiresAt = Date.now() + lease.expiresInSeconds * 1000;
    // Two seconds of slack: the server rounds the seconds down.
    this.capped = extended && lease.expiresInSeconds + 2 < lease.inactivityTimeoutSeconds;
    this.writeShared({ expiresAt: this.expiresAt, capped: this.capped });
  }

  private async end(): Promise<void> {
    if (!this.running) {
      return;
    }
    const reason = this.capped ? SESSION_EXPIRED_REASON : SESSION_TIMEOUT_REASON;
    this.stop();
    await this.keeper.end(reason);
  }

  // Another tab extended the session: take its later end time, which also closes this tab's warning.
  private adoptShared(): void {
    const shared = this.readShared();
    if (shared && this.expiresAt !== null && shared.expiresAt > this.expiresAt) {
      this.expiresAt = shared.expiresAt;
      this.capped = shared.capped;
    }
  }

  private readShared(): SharedExpiry | null {
    try {
      const stored = this.document.defaultView?.localStorage.getItem(SESSION_EXPIRY_STORAGE_KEY);
      const parsed = stored ? (JSON.parse(stored) as Partial<SharedExpiry>) : null;
      return typeof parsed?.expiresAt === 'number' ? { expiresAt: parsed.expiresAt, capped: parsed.capped === true } : null;
    } catch {
      return null;
    }
  }

  private writeShared(expiry: SharedExpiry): void {
    try {
      this.document.defaultView?.localStorage.setItem(SESSION_EXPIRY_STORAGE_KEY, JSON.stringify(expiry));
    } catch {
      // Storage blocked: this tab times out on its own.
    }
  }
}
