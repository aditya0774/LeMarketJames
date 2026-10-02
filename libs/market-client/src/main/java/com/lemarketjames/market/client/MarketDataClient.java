package com.lemarketjames.market.client;

import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link MarketDataService} over HTTP, reached at market-service's {@code /api/market/**}.
 * Since market prices are non-critical read data, a failed or slow call degrades to "no quote"
 * (empty/404) rather than throwing, matching how callers already treated an unsimulated instrument
 * before this feature was ever a separate service (see HoldingsService's zero-valued fallback).
 *
 * <p>Every call reports its outcome to {@link MarketFeedStatus}, so a caller that needs to tell
 * "the feed is down" apart from an ordinary empty result (an unsimulated instrument) can check
 * {@link MarketFeedStatus#isAvailable()}. The connect/read timeouts below mean a hung market-service
 * surfaces as a failure instead of blocking the caller forever.
 *
 * <p>Only created in services that set {@code market.service.url} (core-service, holdings-service).
 * market-service component-scans this library too (it needs the shared model and interface), and
 * without this condition it would get a second {@link MarketDataService} bean next to its own
 * {@code MarketSimulator} and fail to start.
 */
@Service
@ConditionalOnProperty(name = "market.service.url")
public class MarketDataClient implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataClient.class);

    private final RestClient restClient;
    private final MarketFeedStatus feedStatus;

    public MarketDataClient(
            @Value("${market.service.url}") String marketServiceUrl,
            @Value("${market.service.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${market.service.read-timeout-ms:5000}") long readTimeoutMs,
            MarketFeedStatus feedStatus) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(marketServiceUrl).build();
        this.feedStatus = feedStatus;
    }

    @Override
    public Optional<QuoteSnapshot> findByTicker(String ticker) {
        return get("/api/market/quotes/{ticker}", QuoteSnapshot.class, ticker);
    }

    @Override
    public Optional<QuoteSnapshot> findByInstrumentId(int instrumentId) {
        return get("/api/market/quotes/by-instrument/{instrumentId}", QuoteSnapshot.class, instrumentId);
    }

    @Override
    public Collection<QuoteSnapshot> findAll() {
        try {
            QuoteSnapshot[] quotes = restClient.get()
                    .uri("/api/market/quotes")
                    .retrieve()
                    .body(QuoteSnapshot[].class);
            feedStatus.recordSuccess();
            return quotes == null ? List.of() : List.of(quotes);
        } catch (RestClientException e) {
            log.warn("market-service call failed for findAll(): {}", e.getMessage());
            feedStatus.recordFailure();
            return List.of();
        }
    }

    private <T> Optional<T> get(String uriTemplate, Class<T> responseType, Object... uriVariables) {
        try {
            Optional<T> result = Optional.ofNullable(restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { })
                    .body(responseType));
            feedStatus.recordSuccess();
            return result;
        } catch (RestClientException e) {
            log.warn("market-service call failed for {}: {}", uriTemplate, e.getMessage());
            feedStatus.recordFailure();
            return Optional.empty();
        }
    }
}
