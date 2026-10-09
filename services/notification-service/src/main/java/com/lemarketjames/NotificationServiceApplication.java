package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tells clients what happened to their orders. It listens to the order status changes that
 * buy-sell-service publishes to Kafka (contract C6) and keeps one notification per change, so a
 * client who was signed out when an order filled or was rejected still finds out.
 */
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
