package com.lemarketjames.config;

import com.lemarketjames.common.security.AccountAccessCheck;
import com.lemarketjames.common.security.InternalApiTokenFilter;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import java.util.stream.Stream;

/**
 * Spring Security configuration for trading-service, one chain for every feature it serves.
 *
 * <ul>
 *   <li>{@code /internal/**} needs the shared internal token, not a JWT (BR-02): orders-service
 *       calls it server-to-server (settlement and the SELL check), and the test controls live
 *       there too. Settlement moves cash and shares for any account, so a browser session, client
 *       or staff, is refused ({@link InternalApiTokenFilter}). Neither web app routes it either.</li>
 *   <li>{@code GET /api/market/**} needs no credential: orders-service reads prices there without
 *       a cookie, and the quotes carry no user data. Any other method there needs a session.</li>
 *   <li>Notifications are about a client's own orders, so only clients may read them (contract
 *       C7). So is the live portfolio stream. Market activity says how much of each stock traded and never who traded it, so anyone
 *       signed in may read it.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Every notification endpoint lives under this prefix, so one rule guards them all. */
    public static final String NOTIFICATIONS_PATHS = "/api/v1/notifications/**";

    /** The live portfolio stream: a client's own settlements, so clients only. */
    public static final String PORTFOLIO_STREAM_PATH = "/api/v1/portfolio/stream";

    /** Every activity endpoint lives under this prefix, so one rule guards them all. */
    public static final String ACTIVITY_PATHS = "/api/v1/market-activity/**";

    /** The trading app's origins, from a comma-separated setting (contract C5). */
    @Value("${app.cors.allowed-origin}")
    private List<String> allowedOrigins;

    /** Shared with the services that call {@code /internal/**} (contract C5). */
    @Value("${internal.api-token}")
    private String internalApiToken;

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
                .requestMatchers("/", "/actuator/health", "/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/market/**").permitAll()
                // Only another service, by the shared token: a client or staff session gets 403 here.
                .requestMatchers("/internal/**").hasAuthority(InternalApiTokenFilter.AUTHORITY)
                // Staff have no trading account, so there is nothing of theirs to read here.
                .requestMatchers(NOTIFICATIONS_PATHS, PORTFOLIO_STREAM_PATH).hasRole(Role.CLIENT.name())
                .requestMatchers(ACTIVITY_PATHS, "/api/sessions/**").authenticated()
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, accessCheck), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new InternalApiTokenFilter(internalApiToken), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS for a browser that calls this service directly. The web apps call it on their own
     * origin, which needs none; this service's own origin (localhost:8084) stays allowed for local calls.
     *
     * @return a CorsConfigurationSource with the configured CORS settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Stream.concat(allowedOrigins.stream(), Stream.of("http://localhost:8084")).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
