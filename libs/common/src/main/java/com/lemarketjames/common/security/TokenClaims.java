package com.lemarketjames.common.security;

import java.time.Instant;
import java.util.Set;

/**
 * What a valid JWT says about its holder.
 *
 * @param username the login's username (the token subject); clients are looked up by it
 * @param roles    the holder's roles; never empty
 * @param issuedAt  the time the token was issued
 * @param authTime  when the holder signed in; a refreshed token keeps it (BR-03)
 * @param expiresAt when the token stops being accepted
 */
public record TokenClaims(String username, Set<Role> roles, Instant issuedAt, Instant authTime, Instant expiresAt) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
