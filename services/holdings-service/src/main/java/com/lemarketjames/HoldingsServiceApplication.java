package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the holdings microservice (client profile, holdings, portfolio, trade history).
 * Lives in the root package so component, entity, and repository scanning also picks up the
 * shared beans in com.lemarketjames.common (JWT, account/client domain) and market-client's
 * MarketDataClient.
 */
@SpringBootApplication
public class HoldingsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoldingsServiceApplication.class, args);
    }
}
