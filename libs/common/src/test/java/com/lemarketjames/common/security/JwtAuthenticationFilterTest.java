package com.lemarketjames.common.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-14T09:30:00Z");

    private final JwtService jwtService = new JwtService("unit-test-signing-key-please-32bytes-minimum", 3600000);
    // A mock's empty answer means "allowed"; tests that revoke an account stub it.
    private final AccountAccessCheck accessCheck = mock(AccountAccessCheck.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, accessCheck, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Logger securityEventLogger = (Logger) LoggerFactory.getLogger(SecurityEventLog.class);
    private final ListAppender<ILoggingEvent> securityEvents = new ListAppender<>();

    @BeforeEach
    void captureSecurityEvents() {
        SecurityContextHolder.clearContext();
        securityEvents.start();
        securityEventLogger.addAppender(securityEvents);
    }

    @AfterEach
    void tearDown() {
        securityEventLogger.detachAppender(securityEvents);
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

    // LMKT-57 AC2: a token issued before the account was closed is refused on the next request.
    @Test
    void rejectsAValidTokenWhoseAccountWasClosedAfterSignIn() throws Exception {
        when(accessCheck.revokedReason("alice", Set.of(Role.CLIENT))).thenReturn(Optional.of("CLOSED"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/v1/holdings", jwtService.generateToken("alice")), response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType().split(";")[0]);
        assertEquals("{\"success\":false,\"code\":\"ACCOUNT_INACTIVE\","
                + "\"error\":\"This account is no longer active. Please contact the firm.\"}",
                response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(any(), any());
    }

    // A deleted account (no row left) is refused the same way.
    @Test
    void rejectsAValidTokenWhoseAccountNoLongerExists() throws Exception {
        when(accessCheck.revokedReason("ghost", Set.of(Role.CLIENT)))
                .thenReturn(Optional.of(SecurityEventLog.REASON_NOT_FOUND));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/v1/orders", jwtService.generateToken("ghost")), response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void anActiveAccountPassesAndIsCheckedOnce() throws Exception {
        MockHttpServletRequest request = requestTo("/api/v1/holdings", jwtService.generateToken("alice"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(accessCheck).revokedReason("alice", Set.of(Role.CLIENT));
        verify(filterChain).doFilter(request, response);
    }

    // Server-to-server calls, sign-in, registration, sign-out and health are never refused here,
    // so a stale cookie can't block settlement or a fresh sign-in.
    @ParameterizedTest
    @ValueSource(strings = {"/internal/holdings/settle", "/internal/execution/control/pause",
            "/actuator/health", "/api/auth/login", "/api/auth/register", "/api/auth/logout"})
    void uncheckedPathsAreUnaffectedEvenForARevokedAccount(String path) throws Exception {
        when(accessCheck.revokedReason(anyString(), any())).thenReturn(Optional.of("CLOSED"));
        MockHttpServletRequest request = requestTo(path, jwtService.generateToken("alice"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verifyNoInteractions(accessCheck);
        verify(filterChain).doFilter(request, response);
    }

    // No token, or one that doesn't verify, costs no database lookup.
    @Test
    void aMissingOrInvalidTokenNeverReachesTheCheck() throws Exception {
        filter.doFilter(requestTo("/api/v1/holdings", null), new MockHttpServletResponse(), mock(FilterChain.class));
        filter.doFilter(requestTo("/api/v1/holdings", "not-a-valid-token"),
                new MockHttpServletResponse(), mock(FilterChain.class));

        verifyNoInteractions(accessCheck);
    }

    // LMKT-57 AC3: one ACCESS_REVOKED event per rejected request, naming the account, the reason and the UTC time.
    @Test
    void aRejectedRequestIsLoggedAsAccessRevoked() throws Exception {
        when(accessCheck.revokedReason("olivia_ops", Set.of(Role.TRADING_OPS)))
                .thenReturn(Optional.of(SecurityEventLog.REASON_DEACTIVATED));
        String token = jwtService.generateToken("olivia_ops", Set.of(Role.TRADING_OPS));

        filter.doFilter(requestTo("/api/v1/surveillance/alerts", token),
                new MockHttpServletResponse(), mock(FilterChain.class));

        List<String> events = securityEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(List.of("ACCESS_REVOKED username=olivia_ops reason=DEACTIVATED at=2026-10-14T09:30:00Z"), events);
        assertFalse(events.get(0).contains(token), "the token must never be logged");
    }

    private MockHttpServletRequest requestTo(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (token != null) {
            request.setCookies(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token));
        }
        return request;
    }
}
