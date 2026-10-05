package com.lemarketjames.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Confirms the gateway starts and sends each path family to the right service, auth first. */
@SpringBootTest(properties = {
        "services.auth-url=http://auth-service:8082",
        "services.core-url=http://core-service:8081",
        "services.buy-sell-url=http://buy-sell-service:8085"
})
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesPointAtTheirServicesInPriorityOrder() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        Map<String, String> uriById = routes.stream()
                .collect(Collectors.toMap(Route::getId, route -> route.getUri().toString()));
        assertEquals("http://auth-service:8082", uriById.get("auth-service"));
        assertEquals("http://core-service:8081", uriById.get("core-service"));
        assertEquals("http://buy-sell-service:8085", uriById.get("buy-sell-service"));

        // The core route matches /api/**, so auth must be evaluated before it.
        assertEquals("auth-service", routes.get(0).getId());
    }

    @Test
    void ordersResolveToBuySellAndInternalSettlementHasNoRoute() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        for (String path : List.of("/api/v1/orders", "/api/v1/orders/42", "/api/v1/buy-orders",
                "/api/v1/orders/trades/search?orderId=42")) {
            var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get(path));
            assertEquals("buy-sell-service", routes.stream()
                .filter(route -> Boolean.TRUE.equals(reactor.core.publisher.Mono.from(route.getPredicate().apply(exchange)).block()))
                .findFirst().orElseThrow().getId());
        }
        var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.post("/internal/holdings/settle"));
        org.junit.jupiter.api.Assertions.assertTrue(routes.stream().noneMatch(route ->
            Boolean.TRUE.equals(reactor.core.publisher.Mono.from(route.getPredicate().apply(exchange)).block())));
    }
}
