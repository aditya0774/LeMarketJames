import { HttpErrorResponse } from '@angular/common/http';

/**
 * The code the backend puts on a response for an account that may no longer be used (C6,
 * contracts/C6-api.md, AccountInactiveException): 403 at sign-in, 401 on any later request.
 * Match on it, never on the text.
 */
export const ACCOUNT_INACTIVE_CODE = 'ACCOUNT_INACTIVE';

/** The ?reason= value that tells the login page a session was cut off for this. */
export const INACTIVE_REASON = 'inactive';

/** Whether an HTTP error carries ACCOUNT_INACTIVE. Callers check the status they care about. */
export function isAccountInactive(error: unknown): boolean {
  return error instanceof HttpErrorResponse
    && typeof error.error === 'object'
    && error.error?.code === ACCOUNT_INACTIVE_CODE;
}
