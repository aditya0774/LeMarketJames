package com.lemarketjames.common.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /health}: the path the class AWS stack's load balancer checks on every service
 * (deploy/aws/README.md). It answers exactly as {@code /actuator/health} does, database included,
 * with the same status and HTTP code (200 when UP, 503 when DOWN), and shows the status only.
 * {@code /actuator/health} stays as it is, for Docker Compose and Jenkins.
 *
 * <p>Each service permits the path in its own SecurityConfig. The health endpoint is looked up when
 * asked for, so a test slice that loads controllers without the actuator still starts.
 */
@RestController
public class LoadBalancerHealthController {

    private final ObjectProvider<HealthEndpoint> health;
    private final ObjectProvider<HealthEndpointGroups> groups;

    public LoadBalancerHealthController(ObjectProvider<HealthEndpoint> health, ObjectProvider<HealthEndpointGroups> groups) {
        this.health = health;
        this.groups = groups;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        HealthEndpoint endpoint = health.getIfAvailable();
        HealthEndpointGroups healthGroups = groups.getIfAvailable();
        if (endpoint == null || healthGroups == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", Status.UNKNOWN.getCode()));
        }
        Status status = endpoint.health().getStatus();
        int code = healthGroups.getPrimary().getHttpCodeStatusMapper().getStatusCode(status);
        return ResponseEntity.status(code).body(Map.of("status", status.getCode()));
    }
}
