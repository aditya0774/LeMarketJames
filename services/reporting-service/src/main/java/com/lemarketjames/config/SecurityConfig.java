package com.lemarketjames.config;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the reporting service: reports are for analysts only
 * (contract C7). No CORS setup, because no browser calls this service; the staff gateway does.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Every report endpoint lives under this prefix, so one rule guards them all. */
    public static final String REPORTS_PATHS = "/api/v1/reports/**";

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
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(401)))
            .authorizeHttpRequests(auth -> auth
                // Preserve the original failure status during the container's error dispatch.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Reports cover every client's trades, so only analysts may read them.
                .requestMatchers(REPORTS_PATHS).hasRole(Role.ANALYST.name())
                // Nothing else is served here. Denying by default means an endpoint added outside
                // the reports prefix is closed until it gets a rule of its own.
                .anyRequest().denyAll())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
