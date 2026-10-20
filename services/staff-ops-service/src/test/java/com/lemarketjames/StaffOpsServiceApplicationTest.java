package com.lemarketjames;

import com.lemarketjames.config.ReportingDataSourceConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Reports and surveillance share this service but not a connection pool: reports read through a
 * small pool of their own, which PostgreSQL holds to read-only, short queries
 * (ReadSideSafeguardsIntegrationTest), and everything else uses the ordinary one.
 */
@SpringBootTest
class StaffOpsServiceApplicationTest {

    @Autowired DataSource ordinary;
    @Autowired @Qualifier(ReportingDataSourceConfig.DATA_SOURCE) HikariDataSource reporting;

    @Test
    void reportsHaveAPoolOfTheirOwn() {
        assertNotSame(ordinary, reporting);
        assertEquals("reporting", reporting.getPoolName());
        // reporting.datasource.hikari.*'s one connection (LMKT-44), not the ordinary pool's three.
        assertEquals(1, reporting.getMaximumPoolSize());
    }
}
