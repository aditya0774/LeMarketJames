package com.lemarketjames.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

/**
 * The owner's connection to the PostgreSQL test database, next to the application account the
 * tests otherwise use (database/schema/016_audit_lockdown.sql). Tests need it for what the
 * application account may not do.
 *
 * <p>Not configured on H2, which has neither accounts nor the audit triggers.
 */
@Component
class OwnerDatabase {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    OwnerDatabase(@Value("${spring.datasource.url}") String url,
                  @Value("${test.database.owner.username:}") String username,
                  @Value("${test.database.owner.password:}") String password) {
        if (username.isEmpty()) {
            this.jdbc = null;
            this.transaction = null;
        } else {
            // A connection per transaction is enough for a test, so no pool to size or close.
            DriverManagerDataSource owner = new DriverManagerDataSource(url, username, password);
            this.jdbc = new JdbcTemplate(owner);
            this.transaction = new TransactionTemplate(new DataSourceTransactionManager(owner));
        }
    }

    boolean isConfigured() {
        return jdbc != null;
    }

    /** Runs the work as the owner in one transaction, committed when it returns. */
    void inTransaction(Consumer<JdbcTemplate> work) {
        transaction.executeWithoutResult(status -> work.accept(jdbc));
    }

    /**
     * Runs the work as the owner and always rolls it back. For attempts that are expected to be
     * refused: if one ever got through, it would still leave the database untouched.
     */
    void thenRollBack(Consumer<JdbcTemplate> work) {
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.accept(jdbc);
        });
    }
}
