package com.lemarketjames.market.client;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.service.PriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * {@link PriceHistoryService} over HTTP, reached at trading-service's {@code /api/market/history/**}
 * and {@code /api/market/intraday/**}. Like {@link MarketDataClient}, a failed or slow call degrades
 * to an empty history (a chart with nothing to draw) rather than an error, since charts are
 * non-critical read data.
 *
 * <p>Only created in services that set {@code market.service.url}; trading-service itself implements
 * {@link PriceHistoryService} directly.
 */
@Service
@ConditionalOnProperty(name = "market.service.url")
public class PriceHistoryClient implements PriceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryClient.class);

    private final RestClient restClient;

    public PriceHistoryClient(
            @Value("${market.service.url}") String marketServiceUrl,
            @Value("${market.service.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${market.service.read-timeout-ms:5000}") long readTimeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(marketServiceUrl).build();
    }

    @Override
    public List<DailyBar> dailyBars(int instrumentId, LocalDate from) {
        try {
            DailyBar[] bars = restClient.get()
                    .uri("/api/market/history/{instrumentId}?from={from}", instrumentId, from)
                    .retrieve()
                    .body(DailyBar[].class);
            return bars == null ? List.of() : List.of(bars);
        } catch (RestClientException e) {
            log.warn("trading-service history call failed for instrument {}: {}", instrumentId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<PricePoint> intraday(int instrumentId) {
        try {
            PricePoint[] points = restClient.get()
                    .uri("/api/market/intraday/{instrumentId}", instrumentId)
                    .retrieve()
                    .body(PricePoint[].class);
            return points == null ? List.of() : List.of(points);
        } catch (RestClientException e) {
            log.warn("trading-service intraday call failed for instrument {}: {}", instrumentId, e.getMessage());
            return List.of();
        }
    }
}
