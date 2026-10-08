import { Role } from './auth';

/**
 * Where each staff role lands after sign-in: its own dashboard. Listed once; the login page and
 * the start page both read it. A role that is not listed has no section in this app.
 */
const STAFF_HOME: Partial<Record<Role, string>> = {
  TRADING_OPS: '/trading-ops',
  ANALYST: '/analyst',
};

/** Where a signed-in account that may open no page ends up, and where a refused one is sent. */
export const ACCESS_DENIED = '/access-denied';

/** The page an account with these roles starts on. */
export function homeFor(roles: readonly Role[]): string {
  const role = roles.find((candidate) => STAFF_HOME[candidate]);
  return role ? STAFF_HOME[role]! : ACCESS_DENIED;
}
