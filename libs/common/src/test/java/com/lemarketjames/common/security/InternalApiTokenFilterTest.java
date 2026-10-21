package com.lemarketjames.common.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** BR-02: only a service holding the shared token is let into {@code /internal/**}. */
class InternalApiTokenFilterTest {

    private static final String TOKEN = "unit-test-internal-token";

    private final InternalApiTokenFilter filter = new InternalApiTokenFilter(TOKEN);
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(InternalApiTokenFilter.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        logged.start();
        filterLogger.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logged);
        SecurityContextHolder.clearContext();
    }

    /** Runs the filter on a request and returns what it authenticated, checking the chain went on. */
    private Authentication filtered(String path, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (header != null) {
            request.addHeader(InternalApiTokenFilter.HEADER, header);
        }
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        // The filter never answers itself: Spring Security turns "not authenticated" into 401.
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void theRightTokenGrantsTheInternalAuthority() throws Exception {
        Authentication authentication = filtered("/internal/holdings/settle", TOKEN);

        assertEquals(List.of(InternalApiTokenFilter.AUTHORITY),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-token", "unit-test-internal", "unit-test-internal-token-and-more", ""})
    void anyOtherTokenGrantsNothingAndIsLoggedWithoutTheToken(String sent) throws Exception {
        assertNull(filtered("/internal/holdings/settle", sent));

        assertEquals(1, logged.list.size());
        String line = logged.list.get(0).getFormattedMessage();
        assertTrue(line.contains("path=/internal/holdings/settle"), line);
        assertFalse(!sent.isEmpty() && line.contains(sent), "the token sent must never be logged");
        assertFalse(line.contains(TOKEN), "the real token must never be logged");
    }

    @Test
    void noTokenGrantsNothing() throws Exception {
        assertNull(filtered("/internal/holdings/settle", null));
        assertTrue(logged.list.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/orders", "/api/market/quotes", "/actuator/health", "/internalish"})
    void theTokenMeansNothingOutsideInternal(String path) throws Exception {
        assertNull(filtered(path, TOKEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void aServiceWithoutATokenDoesNotStart(String token) {
        assertThrows(IllegalArgumentException.class, () -> new InternalApiTokenFilter(token));
        assertThrows(IllegalArgumentException.class, () -> new InternalApiTokenFilter(null));
    }
}
