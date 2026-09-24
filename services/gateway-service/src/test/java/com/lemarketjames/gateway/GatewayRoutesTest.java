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
        "services.core-url=http://core-service:8081"
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

        // Auth must come before core so /api/auth/** doesn't match the core catch-all /api/**.
        assertEquals("auth-service", routes.get(0).getId());
    }
}
