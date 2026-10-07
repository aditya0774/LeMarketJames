package com.lemarketjames.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sends real requests through the staff gateway to a stand-in for auth-service, to check the
 * cookie a service sees and the cookie the browser gets back (contract C7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("staff")
class StaffSessionCookieTest {

    /** Echoes the Cookie header it received, so a test can see what the gateway forwarded. */
    private static final String SEEN_COOKIE = "X-Seen-Cookie";

    private static final String SESSION_ATTRIBUTES = "; Path=/; Max-Age=3600; HttpOnly; SameSite=Lax";

    private static DisposableServer service;

    @Autowired
    private WebTestClient gateway;

    @DynamicPropertySource
    static void pointAuthRouteAtTheStandIn(DynamicPropertyRegistry registry) {
        service = HttpServer.create().port(0).handle((request, response) -> {
            String cookie = request.requestHeaders().get(HttpHeaders.COOKIE);
            response.addHeader(SEEN_COOKIE, cookie == null ? "" : cookie);
            // Sign-out clears the session the same way sign-in sets it, with an empty value.
            String token = request.uri().endsWith("/logout") ? "" : "issued-token";
            response.addHeader(HttpHeaders.SET_COOKIE, "jwt=" + token + SESSION_ATTRIBUTES);
            response.addHeader(HttpHeaders.SET_COOKIE, "jwt_hint=kept; Path=/");
            return response.sendString(Mono.just("ok"));
        }).bindNow();
        registry.add("services.auth-url", () -> "http://localhost:" + service.port());
    }

    @AfterAll
    static void stopTheStandIn() {
        service.disposeNow();
    }

    @Test
    void theServiceSeesTheStaffSessionAsJwtAndNeverTheCustomers() {
        gateway.get().uri("/api/auth/me")
                .header(HttpHeaders.COOKIE, "jwt=customer-token; staff_jwt=staff-token; theme=dark")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(SEEN_COOKIE, "jwt=staff-token; theme=dark");
    }

    @Test
    void aCustomerSessionAloneIsNotAStaffSession() {
        gateway.get().uri("/api/auth/me")
                .header(HttpHeaders.COOKIE, "jwt=customer-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(SEEN_COOKIE, "");
    }

    @Test
    void signInReachesTheBrowserAsStaffJwtWithItsAttributesKept() {
        List<String> setCookies = gateway.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().get(HttpHeaders.SET_COOKIE);

        // Only the session cookie is renamed; a cookie whose name merely starts with "jwt" is not.
        assertEquals(List.of("staff_jwt=issued-token" + SESSION_ATTRIBUTES, "jwt_hint=kept; Path=/"), setCookies);
    }

    @Test
    void signOutClearsTheStaffCookieNotTheCustomers() {
        List<String> setCookies = gateway.post().uri("/api/auth/logout")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().get(HttpHeaders.SET_COOKIE);

        assertEquals("staff_jwt=" + SESSION_ATTRIBUTES, setCookies.get(0));
    }
}
