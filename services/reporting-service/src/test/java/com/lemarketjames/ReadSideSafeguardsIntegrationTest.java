package com.lemarketjames;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LMKT-138: reports can't starve live trading. Starts the service with its real configuration
 * (src/main/resources/application.properties) and checks each read-side safeguard on the database
 * itself: a small pool, read-only transactions and a query timeout.
 *
 * <p>PostgreSQL only: the limits are PostgreSQL session settings, so there is nothing to check
 * without it. Runs in Jenkins' integration stage; locally, with a database from setup-db.ps1:
 * {@code mvn -B -pl services/reporting-service -am -Dspring.profiles.active=postgres-test
 * -Dtest=ReadSideSafeguardsIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test}.
 */
// A timeout short enough to hit quickly; it also proves the timeout can be set per environment.
@SpringBootTest(properties = "REPORTING_QUERY_TIMEOUT_MS=" + ReadSideSafeguardsIntegrationTest.TIMEOUT_MS)
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*postgres-test.*")
class ReadSideSafeguardsIntegrationTest {

    static final int TIMEOUT_MS = 300;
    /** Hikari's own default: a pool this size or larger would mean the limit is not applied. */
    static final int HIKARI_DEFAULT_POOL_SIZE = 10;

    /** PostgreSQL's code for a write in a read-only transaction. */
    static final String READ_ONLY_TRANSACTION = "25006";
    /** PostgreSQL's code for a statement it cancelled, here because of statement_timeout. */
    static final String QUERY_CANCELED = "57014";

    /** Changes nothing even if it wrongly got through. */
    static final String HARMLESS_WRITE = "UPDATE accounts SET cash_balance = cash_balance WHERE false";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Value("${spring.datasource.hikari.maximum-pool-size}") int configuredPoolSize;

    @Test
    void connectsAsTheRestrictedApplicationAccount() {
        assertEquals("lemarket_app", jdbc.queryForObject("SELECT current_user", String.class));
    }

    @Test
    void poolIsSmall() {
        HikariDataSource pool = assertInstanceOf(HikariDataSource.class, dataSource);
        assertEquals(configuredPoolSize, pool.getMaximumPoolSize());
        assertTrue(pool.getMaximumPoolSize() < HIKARI_DEFAULT_POOL_SIZE,
                "the pool must stay smaller than Hikari's default of " + HIKARI_DEFAULT_POOL_SIZE);
    }

    @Test
    void writeIsRefusedInsideATransaction() {
        refused(READ_ONLY_TRANSACTION, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> jdbc.update(HARMLESS_WRITE)));
    }

    @Test
    void writeIsRefusedOutsideATransactionToo() {
        refused(READ_ONLY_TRANSACTION, () -> jdbc.update(HARMLESS_WRITE));
    }

    @Test
    void queryRunningPastTheTimeoutIsCancelledByTheDatabase() {
        assertEquals(TIMEOUT_MS + "ms", jdbc.queryForObject("SHOW statement_timeout", String.class));
        refused(QUERY_CANCELED, () -> jdbc.queryForList("SELECT pg_sleep(5)"));
    }

    @Test
    void reportingDataSourceIsReadable() {
        // The one source every report reads; the read-only limits must not get in the way of it.
        assertDoesNotThrow(() -> jdbc.queryForObject("SELECT count(*) FROM reporting_trades", Long.class));
    }

    private static void refused(String expectedSqlState, Executable statement) {
        DataAccessException refusal = assertThrows(DataAccessException.class, statement);
        SQLException cause = assertInstanceOf(SQLException.class, refusal.getMostSpecificCause());
        assertEquals(expectedSqlState, cause.getSQLState(), cause.getMessage());
    }
}
