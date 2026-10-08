package com.lemarketjames.config;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the notification service: a notification is about a client's
 * own order, so only clients may read them (contract C7). No CORS setup, because no browser calls
 * this service; the trading gateway does.
 *
 * <p>CSRF protection is left on, unlike in the services that accept POSTs from the browser: this
 * service only answers GET requests, which the protection never blocks, so there is nothing to
 * switch off.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Every notification endpoint lives under this prefix, so one rule guards them all. */
    public static final String NOTIFICATIONS_PATHS = "/api/v1/notifications/**";

    /**
     * Configures the security filter chain for HTTP requests.
     *
     * @param http the HttpSecurity to configure
     * @param jwtService the JWT service for token validation
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(401)))
            .authorizeHttpRequests(auth -> auth
                // Preserve the original failure status during the container's error dispatch.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Staff have no trading account, so there is nothing of theirs to read here.
                .requestMatchers(NOTIFICATIONS_PATHS).hasRole(Role.CLIENT.name())
                // Nothing else is served here; an endpoint added elsewhere is closed until it
                // gets a rule of its own.
                .anyRequest().denyAll())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
