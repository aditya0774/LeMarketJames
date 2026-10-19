package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for staff-ops-service: aggregate, read-only reports for analysts and the
 * surveillance alerts Trading Ops reads. Kept out of the trading services so a heavy report can
 * never slow down order placement; see this module's README for the rules every report follows.
 *
 * <p>Reports read through a pool of their own that the database holds to read-only, short queries
 * ({@link com.lemarketjames.config.ReportingDataSourceConfig}); the alerts the order events raise
 * are written through the ordinary one ({@link com.lemarketjames.config.PrimaryDataSourceConfig}).
 */
@SpringBootApplication
public class StaffOpsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StaffOpsServiceApplication.class, args);
    }
}
