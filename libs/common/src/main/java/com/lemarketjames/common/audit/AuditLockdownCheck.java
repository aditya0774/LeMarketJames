package com.lemarketjames.common.audit;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to start a service whose database account could change or delete audit records
 * (contract C2).
 *
 * <p>The database only protects the audit trail from an account that was never given the right to
 * change it (database/schema/016_audit_lockdown.sql). A service left on the owner account would
 * run normally and quietly keep that right, so the mistake is made loud here instead: the service
 * does not come up.
 */
@Component
public class AuditLockdownCheck {

    /** The audit tables. Mirrors database/schema/016_audit_lockdown.sql. */
    static final List<String> AUDIT_TABLES = List.of("audit_log", "order_events");

    /** The audit tables the connected account holds any change privilege on; a superuser holds all. */
    static final String CHANGEABLE_AUDIT_TABLES =
            "SELECT t FROM unnest(ARRAY['" + String.join("', '", AUDIT_TABLES) + "']) AS t "
                    + "WHERE has_table_privilege(current_user, t, 'UPDATE, DELETE, TRUNCATE')";

    private final JdbcTemplate jdbc;

    public AuditLockdownCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @throws IllegalStateException when the account may update, delete or truncate an audit table */
    @PostConstruct
    public void verify() {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equals(database)) {
            // The H2 schema of the unit tests has no accounts or privileges to check.
            return;
        }
        List<String> changeable = jdbc.queryForList(CHANGEABLE_AUDIT_TABLES, String.class);
        if (!changeable.isEmpty()) {
            String account = jdbc.queryForObject("SELECT current_user", String.class);
            throw new IllegalStateException("Database account '" + account + "' may change or delete audit records ("
                    + String.join(", ", changeable) + "). Services must log in as lemarket_app, which may only "
                    + "insert and read them; see database/README.md (016).");
        }
    }
}
