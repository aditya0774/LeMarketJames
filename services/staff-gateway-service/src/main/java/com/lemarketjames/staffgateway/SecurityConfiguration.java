package com.lemarketjames.staffgateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for the API Gateway.
 * The gateway is a stateless proxy that forwards requests to backend services.
 * Each backend service independently validates JWT tokens and enforces authorization.
 * The gateway only forwards the JWT cookie and routes requests; it does not enforce authentication.
 * This config disables all authentication at the gateway level - backend services handle auth.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // Disable HTTP Basic authentication
            .httpBasic(basic -> basic.disable())
            // Permit ALL requests - backend services enforce auth, not the gateway
            .authorizeExchange(exchanges -> exchanges
                .anyExchange()
                .permitAll()
            )
            // CSRF is disabled for API gateway; frontend handles auth via JWT cookie
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
