package com.lemarketjames.common.security;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Decides, on every authenticated request, whether the account behind a still-valid token may
 * keep using it (LMKT-57). Kept as a small interface so the shared filter never depends on how
 * accounts are stored, and a test that doesn't care can mock it: a mock's empty answer means "allowed".
 */
public interface AccountAccessCheck {

    /**
     * @param username the token's subject
     * @param roles    the token's roles; tells a client token from a staff one
     * @return empty if the account may carry on, otherwise why not: a client's status name,
     *         {@link SecurityEventLog#REASON_DEACTIVATED} or {@link SecurityEventLog#REASON_NOT_FOUND}
     */
    default Optional<String> revokedReason(String username, Set<Role> roles) {
        return revokedReason(username, roles, Instant.MIN);
    }

    Optional<String> revokedReason(String username, Set<Role> roles, Instant issuedAt);
}
