package com.lemarketjames.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Removes what an integration test committed for its own client. The one place these tests delete
 * audit events, so there is a single thing to change when the way they are removed changes.
 *
 * <p>Only the named client's rows go: the disposable PostgreSQL suite also runs other tests, so
 * shared tables are never truncated.
 *
 * <p>On PostgreSQL nobody may delete an audit event (contract C2): the application account lacks
 * the privilege and a trigger refuses everyone else. So the events are removed as the owner, with
 * the trigger switched off inside the deleting transaction. Only the owner can do that, and the
 * table stays locked until the transaction ends, so no other session ever sees it switched off.
 * This is for disposable test data only; nothing in the application can do the same.
 */
@Component
class AuditTestCleanup {

    private static final String CLIENT = "(SELECT client_id FROM clients WHERE username=?)";
    private static final String ACCOUNT =
        "(SELECT a.account_id FROM accounts a JOIN clients c ON a.client_id=c.client_id WHERE c.username=?)";

    private final JdbcTemplate jdbc;
    private final OwnerDatabase owner;

    AuditTestCleanup(JdbcTemplate jdbc, OwnerDatabase owner) {
        this.jdbc = jdbc;
        this.owner = owner;
    }

    /**
     * Removes the client with everything that refers to it. Audit events reference the orders, so
     * they go first. A refused order's events reference the client, and no account when access was
     * refused, so they are found by client as well as by account.
     */
    void removeClient(String username) {
        deletingAuditEvents(db -> {
            db.update("DELETE FROM audit_log WHERE client_id IN " + CLIENT, username);
            db.update("DELETE FROM audit_log WHERE account_id IN " + ACCOUNT, username);
            db.update("DELETE FROM orders WHERE account_id IN " + ACCOUNT, username);
            db.update("DELETE FROM accounts WHERE client_id IN " + CLIENT, username);
            db.update("DELETE FROM addresses WHERE client_id IN " + CLIENT, username);
            db.update("DELETE FROM clients WHERE username=?", username);
        });
    }

    /** Removes one submission's events, for a test that writes events naming no client. */
    void removeSubmission(String requestId) {
        deletingAuditEvents(db -> db.update("DELETE FROM audit_log WHERE request_id=?", requestId));
    }

    private void deletingAuditEvents(Consumer<JdbcTemplate> deletes) {
        if (!owner.isConfigured()) {
            // H2: one account and no trigger, so the test's own connection may delete.
            deletes.accept(jdbc);
            return;
        }
        owner.inTransaction(db -> {
            db.execute("ALTER TABLE audit_log DISABLE TRIGGER audit_log_immutable_row");
            deletes.accept(db);
            // Back to the state 016 leaves it in. A failure above rolls the switch-off back too.
            db.execute("ALTER TABLE audit_log ENABLE ALWAYS TRIGGER audit_log_immutable_row");
        });
    }
}
