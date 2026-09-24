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

        // Actuator route must come first so /actuator/** doesn't get routed to core-service.
        assertEquals("actuator", routes.get(0).getId());
        // Auth must be before core so /api/auth/** doesn't match the core catch-all /api/**.
        var authIndex = routes.stream().map(Route::getId).toList().indexOf("auth-service");
        var coreIndex = routes.stream().map(Route::getId).toList().indexOf("core-service");
        assertEquals(1, authIndex);
        assertEquals(coreIndex, routes.stream().map(Route::getId).toList().size() - 1);
    }
}
