package com.lemarketjames.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Removes what an integration test committed for its own client. The one place these tests delete
 * audit events, so there is a single thing to change when the way they are removed changes.
 *
 * <p>Only the named client's rows go: the disposable PostgreSQL suite also runs other tests, so
 * shared tables are never truncated.
 */
@Component
class AuditTestCleanup {

    private static final String CLIENT = "(SELECT client_id FROM clients WHERE username=?)";
    private static final String ACCOUNT =
        "(SELECT a.account_id FROM accounts a JOIN clients c ON a.client_id=c.client_id WHERE c.username=?)";

    private final JdbcTemplate jdbc;

    AuditTestCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Removes the client with everything that refers to it. Audit events reference the orders, so
     * they go first. A refused order's events reference the client, and no account when access was
     * refused, so they are found by client as well as by account.
     */
    void removeClient(String username) {
        jdbc.update("DELETE FROM audit_log WHERE client_id IN " + CLIENT, username);
        jdbc.update("DELETE FROM audit_log WHERE account_id IN " + ACCOUNT, username);
        jdbc.update("DELETE FROM orders WHERE account_id IN " + ACCOUNT, username);
        jdbc.update("DELETE FROM accounts WHERE client_id IN " + CLIENT, username);
        jdbc.update("DELETE FROM addresses WHERE client_id IN " + CLIENT, username);
        jdbc.update("DELETE FROM clients WHERE username=?", username);
    }

    /** Removes one submission's events, for a test that writes events naming no client. */
    void removeSubmission(String requestId) {
        jdbc.update("DELETE FROM audit_log WHERE request_id=?", requestId);
    }
}
