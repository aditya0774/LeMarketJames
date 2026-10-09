package com.lemarketjames.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Confirms the staff profile serves the staff routes and nothing of the trading gateway's. */
@SpringBootTest(properties = {
        "services.auth-url=http://auth-service:8082",
        "services.buy-sell-url=http://buy-sell-service:8085",
        "services.reporting-url=http://reporting-service:8086",
        "services.surveillance-url=http://surveillance-service:8088"
})
@ActiveProfiles("staff")
class StaffGatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void onlyTheStaffRoutesExist() {
        Map<String, String> uriById = routes().stream()
                .collect(Collectors.toMap(Route::getId, route -> route.getUri().toString()));

        assertEquals(Map.of(
                "staff-reports", "http://reporting-service:8086",
                "staff-auth", "http://auth-service:8082",
                "staff-trading-ops", "http://buy-sell-service:8085",
                "staff-surveillance", "http://surveillance-service:8088"), uriById);
    }

    @Test
    void staffPathsReachTheirServices() {
        assertEquals(Optional.of("staff-reports"), routeFor("/api/v1/reports/ping"));
        assertEquals(Optional.of("staff-reports"), routeFor("/api/v1/reports/trades/by-period"));
        for (String path : List.of("/api/auth/login", "/api/auth/logout", "/api/auth/me")) {
            assertEquals(Optional.of("staff-auth"), routeFor(path), path);
        }
        assertEquals(Optional.of("staff-trading-ops"), routeFor("/api/v1/orders/trades/search?orderId=42"));
        assertEquals(Optional.of("staff-trading-ops"), routeFor("/api/v1/orders/42/timeline"));
        assertEquals(Optional.of("staff-surveillance"), routeFor("/api/v1/surveillance/alerts"));
    }

    @Test
    void everythingElseHasNoRoute() {
        // Customer features, registration, the rest of the orders API and internal paths.
        for (String path : List.of("/", "/api/auth/register", "/api/v1/orders", "/api/v1/orders/42",
                "/api/v1/orders/42/reject", "/api/v1/orders/account/7", "/api/v1/buy-orders",
                "/api/v1/holdings", "/api/v1/instruments", "/api/market/quotes",
                "/api/v1/notifications", "/api/v1/market-activity", "/internal/holdings/settle")) {
            assertEquals(Optional.empty(), routeFor(path), path);
        }
    }

    private List<Route> routes() {
        return routeLocator.getRoutes().collectList().block();
    }

    /** The id of the first route whose predicate accepts a GET of the path, as the gateway picks it. */
    private Optional<String> routeFor(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return routes().stream()
                .filter(route -> Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block()))
                .map(Route::getId)
                .findFirst();
    }
}
