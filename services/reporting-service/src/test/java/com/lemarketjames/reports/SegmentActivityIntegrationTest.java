package com.lemarketjames.reports;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.controller.SegmentActivityController;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.repository.SegmentActivityRepository;
import com.lemarketjames.reports.service.SegmentActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Session-local fixtures shadow the public view without changing shared seed data. */
@SpringBootTest
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*postgres-test.*")
class SegmentActivityIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired SegmentActivityService service;

    @Test
    void readsTheRealReportingViewWithTheServicesReadOnlyConfiguration() {
        assertNotNull(service.report(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)));
    }

    @Test
    void countsDistinctClientsAndTradesWithExactAmountsAndInclusiveReportDates() throws Exception {
        withFixture("""
                CREATE TEMP VIEW reporting_trades AS
                SELECT * FROM (VALUES
                  ('FUTURE_SEGMENT', 1, 10, 10.1234, TIMESTAMP '2026-03-08 05:00:00'),
                  ('FUTURE_SEGMENT', 1, 11, 20.0001, TIMESTAMP '2026-03-08 12:00:00'),
                  ('FUTURE_SEGMENT', 2, 12, 30.0002, TIMESTAMP '2026-03-09 03:59:59.999999'),
                  ('OTHER_SEGMENT', 3, 13, 7.5000, TIMESTAMP '2026-03-08 16:00:00'),
                  ('BEFORE_RANGE', 4, 14, 999.00, TIMESTAMP '2026-03-08 04:59:59.999999'),
                  ('AFTER_RANGE', 5, 15, 999.00, TIMESTAMP '2026-03-09 04:00:00')
                ) AS fixture(segment, client_id, account_id, gross_amount, filled_at)
                """, fixtureService -> {
            var rows = fixtureService.report(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8));
            assertEquals(2, rows.size());
            assertEquals("FUTURE_SEGMENT", rows.get(0).segment());
            assertEquals(2, rows.get(0).activeClients());
            assertEquals(3, rows.get(0).tradeCount());
            assertEquals(0, rows.get(0).totalTradedValue().compareTo(new java.math.BigDecimal("60.1237")));
            assertEquals("OTHER_SEGMENT", rows.get(1).segment());
            assertEquals(1, rows.get(1).activeClients());
            assertEquals(1, rows.get(1).tradeCount());
            assertTrue(fixtureService.report(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)).isEmpty());
        });
    }

    @Test
    void hundredThousandTradeReportCompletesWithinTenSeconds() throws Exception {
        // A repeatable baseline, not a claim about production hardware or concurrent load.
        withFixture("""
                CREATE TEMP VIEW reporting_trades AS
                SELECT 'SEGMENT_' || (n % 5) AS segment, n % 1000 AS client_id,
                       12.3456::numeric AS gross_amount, TIMESTAMP '2026-03-08 12:00:00' AS filled_at
                FROM generate_series(1, 100000) AS n
                """, fixtureService -> {
            var mvc = MockMvcBuilders.standaloneSetup(new SegmentActivityController(fixtureService)).build();
            long started = System.nanoTime();
            mvc.perform(get("/api/v1/reports/activity")
                            .param("startDate", "2026-03-08").param("endDate", "2026-03-08"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(5))
                    .andExpect(jsonPath("$.data[0].activeClients").value(200))
                    .andExpect(jsonPath("$.data[0].tradeCount").value(20000))
                    .andExpect(jsonPath("$.data[0].totalTradedValue").value(246912));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            System.out.println("Segment report PostgreSQL baseline (100,000 trades): " + elapsedMs + " ms");
            assertTrue(elapsedMs < 10_000, "Report exceeded 10 seconds: " + elapsedMs + " ms");
        });
    }

    private void withFixture(String definition, FixtureAssertion assertion) throws Exception {
        // A separate physical connection prevents temporary objects/settings leaking into the pool.
        var source = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
        try (Connection connection = java.sql.DriverManager.getConnection(
                source.getJdbcUrl(), source.getUsername(), source.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute(definition);
            // Fixture DDL is finished before enabling the same query safeguards as the service.
            try (Connection configured = dataSource.getConnection()) {
                var settingsJdbc = new JdbcTemplate(new SingleConnectionDataSource(configured, true));
                String timeout = settingsJdbc.queryForObject("SHOW statement_timeout", String.class);
                jdbc.queryForObject("SELECT set_config('statement_timeout', ?, false)", String.class, timeout);
            }
            jdbc.execute("SET default_transaction_read_only = on");
            var settings = new PlatformSettings();
            settings.getReports().setTimeZone(ZoneId.of("America/New_York"));
            assertion.check(new SegmentActivityService(new SegmentActivityRepository(jdbc),
                    new ReportCalendar(settings)));
        }
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void check(SegmentActivityService service) throws Exception;
    }
}
