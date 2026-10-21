package com.lemarketjames.holdings.client;

import com.lemarketjames.common.security.InternalApiTokenFilter;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.execution.ExecutionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Triggers trading-service to settle a fill (debit/credit cash, upsert the holding). Unlike
 * MarketDataClient's price reads, this is correctness-critical: a failure here must fail the whole
 * status-update request rather than degrade silently, since an order should never end up FILLED
 * without its cash/holdings side effects actually happening.
 */
@Service
public class HoldingsSettlementClient {

    private final RestClient restClient;
    private final ExecutionSettings settings;

    public HoldingsSettlementClient(@Value("${holdings.service.url}") String holdingsServiceUrl,
            @Value("${internal.api-token}") String internalApiToken, ExecutionSettings settings) {
        this.settings = settings;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        // trading-service lets only a holder of the shared token settle (BR-02).
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(holdingsServiceUrl)
                .defaultHeader(InternalApiTokenFilter.HEADER, internalApiToken).build();
    }

    private record Outcome(String rejectionReason) {}

    public void settle(Order order) {
        if (!settings.isSettlementAvailable()) {
            throw new IllegalStateException("Settlement service unavailable");
        }
        SettlementRequest request = new SettlementRequest(
                order.getOrderId(), order.getAccountId(), order.getInstrumentId(),
                order.getOrderType(), order.getQuantity(), order.getPricePerUnit());
        try {
            Outcome outcome = restClient.post()
                    .uri("/internal/holdings/settle")
                    .body(request)
                    .retrieve()
                    .body(Outcome.class);
            if (outcome == null) throw new IllegalStateException("Missing settlement receipt for " + order.getOrderId());
            if (outcome.rejectionReason() != null) throw new SettlementRejectedException(
                com.lemarketjames.orders.entity.RejectionReason.valueOf(outcome.rejectionReason()));
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to settle order " + order.getOrderId() + " with trading-service", e);
        }
    }
}
