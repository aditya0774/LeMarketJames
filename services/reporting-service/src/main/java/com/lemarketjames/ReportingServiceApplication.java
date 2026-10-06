package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aggregate, read-only reports for analysts. Kept out of the trading services so a heavy report
 * can never slow down order placement; see this module's README for the rules every report follows.
 */
@SpringBootApplication
public class ReportingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
