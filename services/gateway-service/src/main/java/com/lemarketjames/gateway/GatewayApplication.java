package com.lemarketjames.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API gateway. All routing lives in application.yml; the gateway only
 * forwards requests (and the jwt cookie) and each service still authenticates them itself.
 *
 * <p>This one module runs as two gateways. By default it is the trading gateway; started with
 * the {@code staff} profile it is the staff gateway, with its own port, its own routes
 * (application-staff.yml) and its own session cookie ({@link StaffSessionCookieGatewayFilterFactory}).
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
