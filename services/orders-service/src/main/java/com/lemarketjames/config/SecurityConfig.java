package com.lemarketjames.config;

import com.lemarketjames.common.security.AccountAccessCheck;
import com.lemarketjames.common.security.InternalApiTokenFilter;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the application.
 * Configures authentication, authorization, CORS, and filters for JWT token handling.
 *
 * <p>Every endpoint but health needs a credential (BR-02). {@code /internal/**} (the execution test
 * control) takes the shared internal token, not a JWT ({@link InternalApiTokenFilter}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /** Shared with the services that serve and call {@code /internal/**} (contract C5). */
    @Value("${internal.api-token}")
    private String internalApiToken;

    /**
     * Configures the security filter chain for HTTP requests.
     * Sets up CORS, CSRF, session management, exception handling, and authorization rules.
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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(401)))
            .authorizeHttpRequests(auth -> auth
                // Preserve the original failure status during the container's error dispatch.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/actuator/health").permitAll()
                // Only another service or a test run, by the shared token: a session gets 403 here.
                .requestMatchers("/internal/**").hasAuthority(InternalApiTokenFilter.AUTHORITY)
                // Dispute searches may read any client's fills, so only operations may call them.
                .requestMatchers("/api/v1/orders/trades/search", "/api/v1/orders/trades/clients").hasRole(Role.TRADING_OPS.name())
                // Moving orders through their lifecycle is an operations task (contract C7).
                .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*/status/*").hasRole(Role.TRADING_OPS.name())
                .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/reject").hasRole(Role.TRADING_OPS.name())
                .requestMatchers("/api/v1/orders/*/timeline").hasRole(Role.TRADING_OPS.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/orders/stream").hasRole(Role.CLIENT.name())
                .requestMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/buy-orders").hasRole(Role.CLIENT.name())
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, accessCheck), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new InternalApiTokenFilter(internalApiToken), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) settings for the application.
     * Allows requests from the configured origin and localhost:8081 (for smoke tests and local development).
     *
     * @return a CorsConfigurationSource with the configured CORS settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow both the frontend (localhost:4200) and backend itself (localhost:8081) for smoke tests
        configuration.setAllowedOrigins(List.of(allowedOrigin, "http://localhost:8081"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
