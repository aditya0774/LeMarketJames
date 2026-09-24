package com.lemarketjames.orders.client;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

/**
 * Reads order data from buy-sell-service for trade history and buying power. buy-sell-service's
 * /api/v1/orders/** endpoints are JWT-ownership-checked, so this relays the incoming request's own
 * jwt cookie rather than bypassing that check — buy-sell-service's authorization is unchanged.
 */
@Service
public class OrdersClient {

    private static final Logger log = LoggerFactory.getLogger(OrdersClient.class);

    private final RestClient restClient;
    private final HttpServletRequest currentRequest;

    public OrdersClient(@Value("${orders.service.url}") String ordersServiceUrl, HttpServletRequest currentRequest) {
        this.restClient = RestClient.builder().baseUrl(ordersServiceUrl).build();
        this.currentRequest = currentRequest;
    }

    public List<OrderSummary> getOrdersForAccount(int accountId) {
        try {
            OrderSummary[] orders = restClient.get()
                    .uri("/api/v1/orders/account/{accountId}", accountId)
                    .header(HttpHeaders.COOKIE, relayedJwtCookieHeader())
                    .retrieve()
                    .body(OrderSummary[].class);
            return orders == null ? List.of() : List.of(orders);
        } catch (RestClientException e) {
            log.warn("buy-sell-service call failed for account {} orders: {}", accountId, e.getMessage());
            return List.of();
        }
    }

    private String relayedJwtCookieHeader() {
        Cookie[] cookies = currentRequest.getCookies();
        String token = cookies == null ? null : Arrays.stream(cookies)
                .filter(c -> JwtAuthenticationFilter.COOKIE_NAME.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
        if (token == null) {
            throw new IllegalStateException("No jwt cookie on the current request to relay to buy-sell-service");
        }
        return JwtAuthenticationFilter.COOKIE_NAME + "=" + token;
    }
}
