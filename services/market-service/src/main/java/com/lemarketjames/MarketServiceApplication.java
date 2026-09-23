package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the market microservice (simulated prices, exposed at /api/market/**).
 * Lives in the root package so component, entity, and repository scanning also picks up
 * com.lemarketjames.market.* below it.
 */
@SpringBootApplication
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }
}
