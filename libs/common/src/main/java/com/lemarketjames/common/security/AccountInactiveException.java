package com.lemarketjames.common.security;

/**
 * An account that may no longer use the platform: a client that is not ACTIVE, a deactivated staff
 * user, or (on the request path) one that no longer exists (LMKT-57). Sign-in and the per-request
 * check both answer with {@link #CODE}, so a front end matches on the code rather than the text.
 */
public class AccountInactiveException extends RuntimeException {

    /** The error code every refusal carries (contract C6). */
    public static final String CODE = "ACCOUNT_INACTIVE";

    /** What the person is told; deliberately the same whatever the reason, so it reveals nothing more. */
    public static final String MESSAGE = "This account is no longer active. Please contact the firm.";

    public AccountInactiveException() {
        super(MESSAGE);
    }
}
