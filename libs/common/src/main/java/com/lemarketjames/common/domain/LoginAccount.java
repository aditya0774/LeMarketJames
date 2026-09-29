package com.lemarketjames.common.domain;

import com.lemarketjames.common.security.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Anything that can log in: a client or a staff user. Lets auth-service verify credentials and
 * apply the lockout rule (contract C5) the same way for both, without knowing which one it has.
 */
public interface LoginAccount {

    String getUsername();

    String getEmail();

    /** BCrypt hash. */
    String getPassword();

    /** The roles written into this login's JWT. */
    Set<Role> roles();

    /** False when the login exists but is barred (e.g. a closed client or a deactivated staff user). */
    boolean canLogIn();

    int getFailedLoginAttempts();

    void setFailedLoginAttempts(int failedLoginAttempts);

    /** When a lockout ends; null or in the past means not locked. */
    Instant getLockedUntil();

    void setLockedUntil(Instant lockedUntil);
}
