package com.lemarketjames.auth;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the session cookie auth-service sets and clears. One place for its attributes, because
 * sign-in, refresh, sign-out and closing an account all set it and must agree on them, or the
 * browser keeps a second cookie instead of replacing the first.
 */
@Component
public class SessionCookies {

    private final boolean secure;

    /**
     * @param secure whether the cookie is marked Secure (HTTPS-only); disable only for plain-HTTP environments
     */
    public SessionCookies(@Value("${auth.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    /** An HTTP-only cookie holding the token, lasting as long as the token. */
    public ResponseCookie issue(String token, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** A cookie that makes the browser forget the session. */
    public ResponseCookie clear() {
        return issue("", 0);
    }
}
