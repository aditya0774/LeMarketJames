package com.lemarketjames.common.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.SimpleHttpCodeStatusMapper;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The load balancer's {@code /health} answers as {@code /actuator/health} does (deploy/aws/README.md). */
class LoadBalancerHealthControllerTest {

    @Test
    void answers200WithTheStatusWhenTheServiceIsUp() {
        ResponseEntity<Map<String, String>> answer = controller(endpointReporting(Health.up().build()), groups()).health();

        assertEquals(200, answer.getStatusCode().value());
        assertEquals(Map.of("status", "UP"), answer.getBody());
    }

    @Test
    void answers503WhenTheServiceIsDown() {
        // A database that has gone away is the usual cause; the load balancer then stops sending traffic.
        ResponseEntity<Map<String, String>> answer = controller(endpointReporting(Health.down().build()), groups()).health();

        assertEquals(503, answer.getStatusCode().value());
        assertEquals(Map.of("status", "DOWN"), answer.getBody());
    }

    @Test
    void answers503UnknownWithoutTheHealthEndpoint() {
        ResponseEntity<Map<String, String>> answer = controller(null, groups()).health();

        assertEquals(503, answer.getStatusCode().value());
        assertEquals(Map.of("status", "UNKNOWN"), answer.getBody());
    }

    @Test
    void answers503UnknownWithoutTheHealthGroups() {
        ResponseEntity<Map<String, String>> answer = controller(endpointReporting(Health.up().build()), null).health();

        assertEquals(503, answer.getStatusCode().value());
        assertEquals(Map.of("status", "UNKNOWN"), answer.getBody());
    }

    private static LoadBalancerHealthController controller(HealthEndpoint endpoint, HealthEndpointGroups groups) {
        return new LoadBalancerHealthController(provider(endpoint), provider(groups));
    }

    private static HealthEndpoint endpointReporting(Health health) {
        HealthEndpoint endpoint = mock(HealthEndpoint.class);
        when(endpoint.health()).thenReturn(health);
        return endpoint;
    }

    /** The actuator's default mapping: 503 for DOWN and OUT_OF_SERVICE, 200 otherwise. */
    private static HealthEndpointGroups groups() {
        HealthEndpointGroup primary = mock(HealthEndpointGroup.class);
        when(primary.getHttpCodeStatusMapper()).thenReturn(new SimpleHttpCodeStatusMapper());
        HealthEndpointGroups groups = mock(HealthEndpointGroups.class);
        when(groups.getPrimary()).thenReturn(primary);
        return groups;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }
}
