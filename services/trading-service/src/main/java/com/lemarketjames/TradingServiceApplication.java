package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for trading-service: the simulated market, instruments, quotes and sessions, the
 * client profile, holdings, portfolio and trade history, and the notification and market-activity
 * consumers. Lives in the root package so component, entity, and repository scanning also picks up
 * the shared beans in com.lemarketjames.common (JWT, account/client domain).
 *
 * <p>Prices come from the market simulator in this same process (see
 * {@code market.service.InProcessMarketData}); market-client's HTTP clients stay switched off
 * because {@code market.service.url} is not set here.
 */
@SpringBootApplication
public class TradingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingServiceApplication.class, args);
    }
}
