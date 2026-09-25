package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.lemarketjames.market.client.MarketDataClient;

/**
 * Entry point for the market microservice (simulated prices, exposed at /api/market/**).
 * Lives in the root package so component, entity, and repository scanning also picks up
 * com.lemarketjames.market.* below it.
 */
@SpringBootApplication
// This service supplies prices locally; the shared HTTP client belongs in consuming services.
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = MarketDataClient.class))
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }
}
