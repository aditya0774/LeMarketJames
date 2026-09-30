package com.lemarketjames.common.security;

import java.util.Set;

/**
 * What a valid JWT says about its holder.
 *
 * @param username the login's username (the token subject); clients are looked up by it
 * @param roles    the holder's roles; never empty
 */
public record TokenClaims(String username, Set<Role> roles) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
