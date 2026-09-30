package com.lemarketjames.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService("unit-test-signing-key-please-32bytes-minimum", 3600000);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // A request carrying a valid JWT cookie should be authenticated as that user and continue the filter chain.
    @Test
    void setsAuthenticationWhenCookieContainsValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtService.generateToken("alice")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    // The token's roles become ROLE_* authorities, which endpoints check with hasRole(...).
    @Test
    void grantsRoleAuthoritiesFromToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.COOKIE_NAME,
                jwtService.generateToken("olivia_ops", Set.of(Role.TRADING_OPS))));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertEquals(List.of("ROLE_TRADING_OPS"), SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    // A request with no JWT cookie at all should be left unauthenticated.
    @Test
    void doesNotSetAuthenticationWhenCookieIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // A request with a garbage/invalid JWT cookie value should be left unauthenticated, not trusted.
    @Test
    void doesNotSetAuthenticationWhenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "not-a-valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
