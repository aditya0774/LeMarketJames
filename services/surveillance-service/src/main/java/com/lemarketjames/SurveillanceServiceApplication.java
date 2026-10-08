package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Watches new orders for Trading Operations. It listens to the orders that buy-sell-service
 * announces on Kafka as they are placed (contract C6) and raises an alert for each one large
 * enough to deserve a look, so staff see it without searching for it.
 */
@SpringBootApplication
public class SurveillanceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SurveillanceServiceApplication.class, args);
    }
}
