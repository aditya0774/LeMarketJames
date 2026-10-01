package com.lemarketjames.holdings.client;

import com.lemarketjames.orders.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Triggers holdings-service to settle a fill (debit/credit cash, upsert the holding). Unlike
 * MarketDataClient's price reads, this is correctness-critical: a failure here must fail the whole
 * status-update request rather than degrade silently, since an order should never end up FILLED
 * without its cash/holdings side effects actually happening.
 */
@Service
public class HoldingsSettlementClient {

    private final RestClient restClient;

    public HoldingsSettlementClient(@Value("${holdings.service.url}") String holdingsServiceUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(holdingsServiceUrl).build();
    }

    private record Outcome(String rejectionReason) {}

    public void settle(Order order) {
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
            throw new IllegalStateException("Failed to settle order " + order.getOrderId() + " with holdings-service", e);
        }
    }
}
