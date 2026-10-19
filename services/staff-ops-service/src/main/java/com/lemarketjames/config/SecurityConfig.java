package com.lemarketjames.config;

import com.lemarketjames.common.security.AccountAccessCheck;
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
 * Spring Security configuration for staff-ops-service (contract C7): reports are for analysts
 * only, surveillance alerts for Trading Ops only. No CORS setup, because the staff app calls this
 * service on its own origin, through its Nginx or dev proxy.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Every report endpoint lives under this prefix, so one rule guards them all. */
    public static final String REPORTS_PATHS = "/api/v1/reports/**";

    /** Every surveillance endpoint lives under this prefix, so one rule guards them all. */
    public static final String SURVEILLANCE_PATHS = "/api/v1/surveillance/**";

    /**
     * Configures the security filter chain for HTTP requests.
     *
     * @param http the HttpSecurity to configure
     * @param jwtService the JWT service for token validation
     * @param accessCheck ends the session of an account that may no longer be used (LMKT-57)
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   AccountAccessCheck accessCheck) throws Exception {
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
                // An alert shows whose order it was and how big, so clients and analysts are refused.
                .requestMatchers(SURVEILLANCE_PATHS).hasRole(Role.TRADING_OPS.name())
                // Nothing else is served here. Denying by default means an endpoint added outside
                // these two prefixes is closed until it gets a rule of its own.
                .anyRequest().denyAll())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, accessCheck), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
