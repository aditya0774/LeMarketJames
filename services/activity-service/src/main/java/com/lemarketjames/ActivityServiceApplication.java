package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Shows how much of each stock has been trading. It listens to the fills that buy-sell-service
 * publishes to Kafka (contract C6), keeps one row per fill and sums them into the traded volume
 * per stock over the last day, without ever asking the order tables.
 */
@SpringBootApplication
public class ActivityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivityServiceApplication.class, args);
    }
}
