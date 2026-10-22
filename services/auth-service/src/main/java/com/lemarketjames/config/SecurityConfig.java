package com.lemarketjames.config;

import com.lemarketjames.common.security.AccountAccessCheck;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
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
 * Spring Security configuration for the auth service.
 * Register and login are public; everything else (e.g. /api/auth/me) needs a valid JWT cookie.
 * Account status changes are Trading Ops' and closing an account is the client's own (contract C7).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The trading app's origins, from a comma-separated setting (contract C5). */
    @Value("${app.cors.allowed-origin}")
    private List<String> allowedOrigins;

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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(401)))
            .authorizeHttpRequests(auth -> auth
                // Preserve the original failure status during the container's error dispatch.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/actuator/health", "/health").permitAll()
                .requestMatchers("/api/v1/clients/*/status").hasRole(Role.TRADING_OPS.name())
                .requestMatchers("/api/auth/close-account").hasRole(Role.CLIENT.name())
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, accessCheck), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS for direct calls to this service during local development. Browser traffic normally
     * arrives through a web app on the browser's own origin, which needs no CORS.
     *
     * @return a CorsConfigurationSource with the configured CORS settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
