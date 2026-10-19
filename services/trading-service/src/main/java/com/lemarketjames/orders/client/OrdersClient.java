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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads order data from orders-service for trade history and buying power. orders-service's
 * /api/v1/orders/** endpoints are JWT-ownership-checked, so this relays the incoming request's own
 * jwt cookie rather than bypassing that check — orders-service's authorization is unchanged.
 */
@Service
public class OrdersClient {

    private static final Logger log = LoggerFactory.getLogger(OrdersClient.class);

    /** The largest page orders-service serves (its {@code MAX_SIZE}). */
    static final int PAGE_SIZE = 100;
    /** A bound on the paging loop, far above any real account (100,000 orders). */
    private static final int MAX_PAGES = 1000;

    private final RestClient restClient;
    private final HttpServletRequest currentRequest;

    public OrdersClient(@Value("${orders.service.url}") String ordersServiceUrl, HttpServletRequest currentRequest) {
        this.restClient = RestClient.builder().baseUrl(ordersServiceUrl).build();
        this.currentRequest = currentRequest;
    }

    /**
     * Every order of the account. orders-service pages this endpoint (20 orders by default), so
     * the pages are read in turn until a short one: buying power, trade history and the portfolio
     * chart all need the whole list, not the latest page.
     */
    public List<OrderSummary> getOrdersForAccount(int accountId) {
        try {
            String cookie = relayedJwtCookieHeader();
            List<OrderSummary> all = new ArrayList<>();
            for (int page = 0; page < MAX_PAGES; page++) {
                OrderSummary[] orders = restClient.get()
                        .uri("/api/v1/orders/account/{accountId}?page={page}&size={size}", accountId, page, PAGE_SIZE)
                        .header(HttpHeaders.COOKIE, cookie)
                        .retrieve()
                        .body(OrderSummary[].class);
                if (orders == null) {
                    break;
                }
                all.addAll(Arrays.asList(orders));
                if (orders.length < PAGE_SIZE) {
                    break;
                }
            }
            return List.copyOf(all);
        } catch (RestClientException e) {
            log.warn("orders-service call failed for account {} orders: {}", accountId, e.getMessage());
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
            throw new IllegalStateException("No jwt cookie on the current request to relay to orders-service");
        }
        return JwtAuthenticationFilter.COOKIE_NAME + "=" + token;
    }
}
