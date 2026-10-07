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

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A browser adds an Origin header to every POST, so the staff app's sign-in and sign-out carry
 * the staff app's origin. The services accept only the trading app's origin, so the staff
 * gateway has to decide CORS itself and not pass Origin on. Sends real requests through the
 * staff gateway to a stand-in that refuses any request with an Origin, as auth-service refuses
 * the staff app's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "STAFF_CORS_ALLOWED_ORIGIN=" + StaffGatewayCorsTest.STAFF_APP)
@ActiveProfiles("staff")
class StaffGatewayCorsTest {

    static final String STAFF_APP = "http://staff.example:4201";

    private static final AtomicInteger requestsSeen = new AtomicInteger();

    private static DisposableServer service;

    @Autowired
    private WebTestClient gateway;

    @DynamicPropertySource
    static void pointAuthRouteAtTheStandIn(DynamicPropertyRegistry registry) {
        service = HttpServer.create().port(0).handle((request, response) -> {
            requestsSeen.incrementAndGet();
            if (request.requestHeaders().contains(HttpHeaders.ORIGIN)) {
                return response.status(403).sendString(Mono.just("Invalid CORS request"));
            }
            return response.sendString(Mono.just("signed in"));
        }).bindNow();
        registry.add("services.auth-url", () -> "http://localhost:" + service.port());
    }

    @AfterAll
    static void stopTheStandIn() {
        service.disposeNow();
    }

    @Test
    void signInFromTheStaffAppReachesTheServiceWithoutItsOrigin() {
        gateway.post().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, STAFF_APP)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("signed in");
    }

    @Test
    void theGatewayStillAnswersTheStaffAppsOwnCorsCheck() {
        gateway.post().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, STAFF_APP)
                .exchange()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, STAFF_APP);
    }

    @Test
    void anotherOriginIsRefusedByTheGatewayAndNeverReachesTheService() {
        int before = requestsSeen.get();

        gateway.post().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://elsewhere.example")
                .exchange()
                .expectStatus().isForbidden();

        assertEquals(before, requestsSeen.get());
    }
}
