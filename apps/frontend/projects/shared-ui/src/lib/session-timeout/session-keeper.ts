import { InjectionToken } from '@angular/core';

/**
 * What auth-service says about how long a session lasts (BR-03, contracts/C6-api.md): on sign-in,
 * GET /api/auth/me and POST /api/auth/refresh. Durations, not times, so the browser's clock being
 * off doesn't matter.
 */
export interface SessionLease {
  /** Seconds until the session ends unless it is extended. */
  expiresInSeconds: number;
  /** The idle limit (lmj.session.inactivity-timeout, contracts/C5-config.md). */
  inactivityTimeoutSeconds: number;
}

/**
 * Why a session was ended for the user, sent to the login page as ?reason=: timeout when nobody
 * used the app for the inactivity timeout, expired when the session reached its time limit
 * (jwt.expiration-ms) however much it was used.
 */
export type SessionEndReason = 'timeout' | 'expired';

export const SESSION_TIMEOUT_REASON: SessionEndReason = 'timeout';
export const SESSION_EXPIRED_REASON: SessionEndReason = 'expired';

/** What both apps' login pages say for each reason, so the two read the same. */
export const SESSION_END_MESSAGES: Readonly<Record<SessionEndReason, { heading: string; message: string }>> = {
  timeout: { heading: 'Signed out', message: 'You were signed out after a period of inactivity. Log in again to continue.' },
  expired: { heading: 'Session ended', message: 'Your session reached its time limit. Log in again to continue.' },
};

/** The message for a login page's ?reason=, if it is a session end reason. */
export function sessionEndMessage(reason: string | null): { heading: string; message: string } | null {
  return reason === SESSION_TIMEOUT_REASON || reason === SESSION_EXPIRED_REASON ? SESSION_END_MESSAGES[reason] : null;
}

/**
 * The calls the session timeout needs from an app's back end. Shared UI never calls the back end
 * itself (each app owns its services), so each app provides one of these under SESSION_KEEPER.
 */
export interface SessionKeeper {
  /** The lease sign-in or the page load gave the session, if known. */
  current(): SessionLease | null;
  /** Extends the session (POST /api/auth/refresh); rejects if it can't be extended. */
  extend(): Promise<SessionLease>;
  /** Ends the session and takes the user to the login page with the reason shown. */
  end(reason: SessionEndReason): Promise<void>;
}

export const SESSION_KEEPER = new InjectionToken<SessionKeeper>('SessionKeeper');
