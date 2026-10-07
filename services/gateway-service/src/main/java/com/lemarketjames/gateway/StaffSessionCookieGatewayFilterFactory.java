package com.lemarketjames.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Gives staff their own session cookie, so a staff session and a customer session can exist in
 * one browser. Browsers share cookies across ports of a host: with both apps on the same host and
 * both using {@code jwt}, signing in to one would sign the other out.
 *
 * <p>The staff gateway is the only place that knows the staff name. The browser holds
 * {@code staff_jwt}; the services still set and read {@code jwt}, and this filter renames it in
 * both directions, so no service changes. Applied through the staff profile's
 * {@code default-filters} (application-staff.yml) as {@code StaffSessionCookie}; the trading
 * gateway never uses it.
 */
@Component
public class StaffSessionCookieGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    /**
     * The cookie every service sets and reads. Mirrors {@code JwtAuthenticationFilter.COOKIE_NAME}
     * in libs/common, which this reactive module can't depend on; change them together.
     */
    static final String SERVICE_COOKIE = "jwt";

    /** The cookie the staff app's browser holds (contract C7). */
    static final String STAFF_COOKIE = "staff_jwt";

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> translateRequestCookies(headers, exchange.getRequest().getCookies()))
                    .build();
            return chain.filter(exchange.mutate().request(request).build())
                    // Runs once the service has answered and before the response is written.
                    .then(Mono.fromRunnable(() -> translateSetCookie(exchange.getResponse().getHeaders())));
        };
    }

    /**
     * Rebuilds the Cookie header for the service. The customer's {@code jwt} is dropped first:
     * the browser sends it to the staff port too, and it must never count as a staff session.
     */
    private static void translateRequestCookies(HttpHeaders headers, MultiValueMap<String, HttpCookie> cookies) {
        String translated = cookies.values().stream()
                .flatMap(List::stream)
                .filter(cookie -> !SERVICE_COOKIE.equals(cookie.getName()))
                .map(cookie -> (STAFF_COOKIE.equals(cookie.getName()) ? SERVICE_COOKIE : cookie.getName())
                        + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
        if (translated.isEmpty()) {
            headers.remove(HttpHeaders.COOKIE);
        } else {
            headers.set(HttpHeaders.COOKIE, translated);
        }
    }

    /** Renames the service's cookie on its way to the browser; its attributes are kept as sent. */
    private static void translateSetCookie(HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return;
        }
        String servicePrefix = SERVICE_COOKIE + "=";
        headers.put(HttpHeaders.SET_COOKIE, setCookies.stream()
                .map(value -> value.startsWith(servicePrefix)
                        ? STAFF_COOKIE + "=" + value.substring(servicePrefix.length())
                        : value)
                .toList());
    }
}
