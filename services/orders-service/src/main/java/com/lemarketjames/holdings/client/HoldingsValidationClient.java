package com.lemarketjames.holdings.client;

import com.lemarketjames.common.security.InternalApiTokenFilter;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Re-checks a SELL order's holdings with trading-service before it is persisted. The browser's own
 * pre-flight call to {@code /api/v1/holdings/validate} is advisory only, so this server-to-server
 * call is what actually guards against overselling.
 */
@Service
public class HoldingsValidationClient {

    private final RestClient restClient;

    public HoldingsValidationClient(@Value("${holdings.service.url}") String holdingsServiceUrl,
            @Value("${internal.api-token}") String internalApiToken) {
        // trading-service answers /internal/** only to a holder of the shared token (BR-02).
        this.restClient = RestClient.builder().baseUrl(holdingsServiceUrl)
                .defaultHeader(InternalApiTokenFilter.HEADER, internalApiToken).build();
    }

    /**
     * @param reservedQuantity shares of the stock already in the account's open SELL orders
     * @throws InsufficientHoldingsException if the holding less {@code reservedQuantity} is below {@code sellQuantity}
     */
    public void validateSufficientHoldings(Integer accountId, String username, Integer instrumentId,
                                           BigDecimal sellQuantity, BigDecimal reservedQuantity) {
        HoldingsValidationRequest request = new HoldingsValidationRequest(accountId, username, instrumentId,
            sellQuantity, reservedQuantity);
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
                "Failed to validate holdings for accountId " + accountId + " with trading-service", e);
        }
    }

    private String extractError(HttpClientErrorException.BadRequest e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            Object error = body != null ? body.get("error") : null;
            return error != null ? error.toString() : "Insufficient holdings";
        } catch (RestClientException | IllegalStateException parseFailure) {
            // An unreadable body (RestClientException) or none the client can convert (IllegalStateException).
            return "Insufficient holdings";
        }
    }
}
