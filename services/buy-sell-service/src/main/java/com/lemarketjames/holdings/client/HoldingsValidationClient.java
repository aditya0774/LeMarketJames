package com.lemarketjames.holdings.client;

import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Re-checks a SELL order's holdings with holdings-service before it is persisted. The browser's own
 * pre-flight call to {@code /api/v1/holdings/validate} is advisory only, so this server-to-server
 * call is what actually guards against overselling.
 */
@Service
public class HoldingsValidationClient {

    private final RestClient restClient;

    public HoldingsValidationClient(@Value("${holdings.service.url}") String holdingsServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(holdingsServiceUrl).build();
    }

    public void validateSufficientHoldings(Integer accountId, String username,
                                           Integer instrumentId, BigDecimal sellQuantity) {
        HoldingsValidationRequest request = new HoldingsValidationRequest(accountId, username, instrumentId, sellQuantity);
        try {
            restClient.post()
                    .uri("/internal/holdings/validate")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InsufficientHoldingsException(extractError(e));
        } catch (RestClientException e) {
            throw new IllegalStateException(
                "Failed to validate holdings for accountId " + accountId + " with holdings-service", e);
        }
    }

    private String extractError(HttpClientErrorException.BadRequest e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            Object error = body != null ? body.get("error") : null;
            return error != null ? error.toString() : "Insufficient holdings";
        } catch (Exception parseFailure) {
            return "Insufficient holdings";
        }
    }
}
