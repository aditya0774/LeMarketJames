package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the buy-sell microservice (order placement, validation, execution, status,
 * and history). Lives in the root package so component, entity, and repository scanning also
 * picks up the shared beans in com.lemarketjames.common (JWT, account/client domain).
 */
@SpringBootApplication
public class BuySellServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuySellServiceApplication.class, args);
    }
}
