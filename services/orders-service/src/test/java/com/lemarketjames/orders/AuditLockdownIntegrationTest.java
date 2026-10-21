package com.lemarketjames.orders;

import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditLockdownCheck;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.audit.SubmissionAuditEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LMKT-100: an audit record can't be changed or deleted, whoever tries (contract C2,
 * database/schema/016_audit_lockdown.sql). The application account lacks the privilege, and a
 * trigger refuses everyone else, the owner included. BR-14 extends that to the trade records
 * (029): a final order, the settlement and cash receipts and the account status history.
 *
 * <p>PostgreSQL only: accounts, grants and triggers are the database's, so the H2 unit-test schema
 * has none of them. Every attempt runs in a transaction that is rolled back whatever happens, so
 * one that wrongly got through still could not damage the database it ran against.
 */
@SpringBootTest
class AuditLockdownIntegrationTest {

    /** PostgreSQL's own code for a missing privilege. */
    static final String PERMISSION_DENIED = "42501";
    /** Raised by the triggers in 016 and 029. */
    static final String AUDIT_IMMUTABLE = "LM001";
    /** The insert-only trade records (029). */
    static final List<String> INSERT_ONLY = List.of("settlement_receipts", "cash_movement_receipts", "account_status_changes");
    /** A throwaway order on the first seeded account, in the given status; always rolled back. */
    static final String NEW_ORDER = "INSERT INTO orders (account_id, instrument_id, order_type, quantity, order_status) "
        + "SELECT min(account_id), (SELECT min(instrument_id) FROM instruments), 'BUY', 1, ? FROM accounts RETURNING order_id";

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AuditRecorder recorder;
    @Autowired AuditEventRepository audit;
    @Autowired OwnerDatabase owner;
    @Autowired AuditTestCleanup cleanup;
    String requestId;

    @BeforeEach
    void recordOneEventAsTheApplication() {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        assumeTrue("PostgreSQL".equals(database), "the lockdown is PostgreSQL's; run with the postgres-test profile");

        // A refused submission's first event: it needs no order, account or client to exist.
        String id = UUID.randomUUID().toString();
        new TransactionTemplate(transactions).executeWithoutResult(status -> recorder.recordSubmission(
            new SubmissionAuditEvent(id, AuditEventType.SUBMITTED, null, null, null, null, Map.of("side", "BUY"))));
        requestId = id;
    }

    @AfterEach
    void removeTheEvent() {
        if (requestId != null) {
            cleanup.removeSubmission(requestId);
        }
    }

    @Test
    void theApplicationStillInsertsAndReadsAuditEvents() {
        List<AuditEventEntity> trail = audit.findByRequestIdOrderByAuditIdAsc(requestId);

        assertEquals(1, trail.size());
        assertEquals(AuditEventType.SUBMITTED, trail.get(0).getEventType());
        assertEquals(Map.of("side", "BUY"), trail.get(0).getDetails());
    }

    @Test
    void theApplicationAccountIsNeitherOwnerNorSuperuser() {
        assertFalse(jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname = 'public' "
            + "AND tableowner = current_user", Integer.class), "it owns no table, so it can't grant itself anything");
    }

    // This context started, so the startup check accepted the application account. The owner is
    // what a service must never run as: the same check refuses it.
    @Test
    void aServiceRefusesToStartOnTheOwnerAccount() {
        owner.thenRollBack(db -> {
            AuditLockdownCheck check = new AuditLockdownCheck(db);
            IllegalStateException refused = assertThrows(IllegalStateException.class, check::verify);
            assertTrue(refused.getMessage().contains("audit_log, order_events"), refused.getMessage());
        });
    }

    @Test
    void theApplicationAccountHoldsOnlyInsertAndSelectOnTheAuditTables() {
        for (String table : List.of("audit_log", "order_events")) {
            for (String allowed : List.of("SELECT", "INSERT")) {
                assertTrue(hasPrivilege(table, allowed), allowed + " on " + table);
            }
            for (String withheld : List.of("UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                assertFalse(hasPrivilege(table, withheld), withheld + " on " + table);
            }
        }
    }

    @Test
    void theApplicationAccountCannotUpdateAnAuditEvent() {
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("UPDATE audit_log SET archived=TRUE WHERE request_id=?", requestId));
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("UPDATE audit_log SET details='{}'::jsonb WHERE request_id=?", requestId));
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("UPDATE order_events SET event_type='CHANGED'"));

        assertTheEventIsUntouched();
    }

    @Test
    void theApplicationAccountCannotDeleteAnAuditEvent() {
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("DELETE FROM audit_log WHERE request_id=?", requestId));
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("DELETE FROM order_events"));
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("TRUNCATE audit_log"));
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("TRUNCATE order_events"));

        assertTheEventIsUntouched();
    }

    // The second layer: the owner holds every privilege on its own tables, so only the trigger
    // stands between a migration, or anyone using the owner's password, and the audit trail.
    @Test
    void theOwnerIsRefusedByTheTrigger() {
        assertRefused(AUDIT_IMMUTABLE, () -> asTheOwner("UPDATE audit_log SET archived=TRUE WHERE request_id=?", requestId));
        assertRefused(AUDIT_IMMUTABLE, () -> asTheOwner("DELETE FROM audit_log WHERE request_id=?", requestId));
        assertRefused(AUDIT_IMMUTABLE, () -> asTheOwner("TRUNCATE audit_log"));
        assertRefused(AUDIT_IMMUTABLE, () -> asTheOwner("TRUNCATE order_events"));

        assertTheEventIsUntouched();
    }

    // What lets the re-applied migrations 009, 010 and 015 through: their old backfills match no
    // rows any more, and a statement that touches no audit record is not an attempt on one.
    @Test
    void aStatementThatTouchesNoAuditRecordIsNotRefused() {
        assertDoesNotThrow(() -> asTheOwner("UPDATE audit_log SET archived=TRUE WHERE request_id=?", "no-such-request"));
        assertDoesNotThrow(() -> asTheOwner("DELETE FROM audit_log WHERE request_id=?", "no-such-request"));
    }

    @Test
    void everyTriggerIsArmedEvenInReplicaMode() {
        // 'A' is ENABLE ALWAYS: it fires whatever session_replication_role says.
        List<String> triggers = List.of("audit_log_immutable_row", "audit_log_immutable_truncate",
            "order_events_immutable_row", "order_events_immutable_truncate",
            "orders_final_immutable", "orders_no_delete", "orders_no_truncate",
            "settlement_receipts_immutable_row", "settlement_receipts_immutable_truncate",
            "cash_movement_receipts_immutable_row", "cash_movement_receipts_immutable_truncate",
            "account_status_changes_immutable_row", "account_status_changes_immutable_truncate");
        assertEquals(triggers.size(), jdbc.queryForObject(
            "SELECT count(*) FROM pg_trigger WHERE tgenabled = 'A' AND tgname = ANY (?)", Integer.class,
            (Object) triggers.toArray(String[]::new)));
    }

    // BR-14 (029): the trade records. The application keeps what it needs and nothing more.
    @Test
    void theApplicationAccountMayMoveOrdersButNeverRemoveThem() {
        for (String allowed : List.of("SELECT", "INSERT", "UPDATE")) {
            assertTrue(hasPrivilege("orders", allowed), allowed + " on orders");
        }
        for (String withheld : List.of("DELETE", "TRUNCATE")) {
            assertFalse(hasPrivilege("orders", withheld), withheld + " on orders");
        }
        for (String table : INSERT_ONLY) {
            assertTrue(hasPrivilege(table, "SELECT") && hasPrivilege(table, "INSERT"), "read and add " + table);
            for (String withheld : List.of("UPDATE", "DELETE", "TRUNCATE")) {
                assertFalse(hasPrivilege(table, withheld), withheld + " on " + table);
            }
        }
    }

    @Test
    void anOpenOrderStillMovesAlongItsLifecycleUntilItIsFinal() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            status.setRollbackOnly();
            Integer orderId = jdbc.queryForObject(NEW_ORDER, Integer.class, "SUBMITTED");
            assertEquals(1, jdbc.update("UPDATE orders SET order_status='ACCEPTED' WHERE order_id=?", orderId));
            // The move into a final status is the last update the order ever gets.
            assertEquals(1, jdbc.update("UPDATE orders SET order_status='FILLED', price_per_unit=1 WHERE order_id=?", orderId));
        });
    }

    @Test
    void nobodyChangesAFinalOrder() {
        for (String status : List.of("FILLED", "REJECTED")) {
            assertRefused(AUDIT_IMMUTABLE, () -> asTheApplicationOn(NEW_ORDER, status,
                "UPDATE orders SET price_per_unit=1 WHERE order_id=?"));
            assertRefused(AUDIT_IMMUTABLE, () -> asTheOwnerOn(NEW_ORDER, status,
                "UPDATE orders SET order_status='ACCEPTED' WHERE order_id=?"));
        }
        assertRefused(AUDIT_IMMUTABLE, () -> asTheApplication("UPDATE orders SET price_per_unit=1 WHERE order_status='FILLED'"));
    }

    @Test
    void nobodyDeletesAnOrder() {
        assertRefused(PERMISSION_DENIED, () -> asTheApplicationOn(NEW_ORDER, "SUBMITTED", "DELETE FROM orders WHERE order_id=?"));
        assertRefused(PERMISSION_DENIED, () -> asTheApplication("TRUNCATE orders CASCADE"));
        assertRefused(AUDIT_IMMUTABLE, () -> asTheOwnerOn(NEW_ORDER, "SUBMITTED", "DELETE FROM orders WHERE order_id=?"));
        assertRefused(AUDIT_IMMUTABLE, () -> asTheOwner("TRUNCATE orders CASCADE"));
    }

    @Test
    void nobodyChangesOrDeletesAReceiptOrAStatusChange() {
        String settlement = "INSERT INTO settlement_receipts (order_id, account_id, instrument_id, order_type, quantity, price_per_unit) "
            + "VALUES (-1, 1, 1, ?, 1, 1) RETURNING order_id";
        String cash = "INSERT INTO cash_movement_receipts (request_id, account_id, movement_type, amount, cash_balance_after) "
            + "SELECT gen_random_uuid(), min(account_id), ?, 1, 1 FROM accounts RETURNING account_id";
        String statusChange = "INSERT INTO account_status_changes (client_id, old_status, new_status, reason, changed_by) "
            + "SELECT min(client_id), 'ACTIVE', ?, 'BR-14 lockdown test', 'test' FROM clients RETURNING client_id";
        // Each row is added and attacked in one rolled-back transaction, so no test row is ever kept.
        Map<String, String[]> attempts = Map.of(
            settlement, new String[] {"BUY", "UPDATE settlement_receipts SET quantity=2 WHERE order_id=?",
                "DELETE FROM settlement_receipts WHERE order_id=?"},
            cash, new String[] {"DEPOSIT", "UPDATE cash_movement_receipts SET amount=2 WHERE account_id=?",
                "DELETE FROM cash_movement_receipts WHERE account_id=?"},
            statusChange, new String[] {"SUSPENDED", "UPDATE account_status_changes SET reason='changed' WHERE client_id=?",
                "DELETE FROM account_status_changes WHERE client_id=?"});
        attempts.forEach((insert, attempt) -> {
            for (String change : List.of(attempt[1], attempt[2])) {
                assertRefused(PERMISSION_DENIED, () -> asTheApplicationOn(insert, attempt[0], change));
                assertRefused(AUDIT_IMMUTABLE, () -> asTheOwnerOn(insert, attempt[0], change));
            }
        });
        for (String table : INSERT_ONLY) {
            assertRefused(PERMISSION_DENIED, () -> asTheApplication("TRUNCATE " + table));
            assertRefused(AUDIT_IMMUTABLE, () -> asTheOwner("TRUNCATE " + table));
        }
    }

    // The tests' own reset (AuditTestCleanup) is the one sanctioned way around the trigger. It has
    // to work, and it has to leave the trigger exactly as it found it.
    @Test
    void theTestCleanupRemovesItsEventsAndLeavesTheTriggerArmed() {
        cleanup.removeSubmission(requestId);

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE request_id=?", Integer.class, requestId));
        assertEquals("A", jdbc.queryForObject(
            "SELECT tgenabled::text FROM pg_trigger WHERE tgname = 'audit_log_immutable_row'", String.class));
    }

    private boolean hasPrivilege(String table, String privilege) {
        return jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, ?)", Boolean.class, table, privilege);
    }

    private void asTheApplication(String sql, Object... args) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update(sql, args);
        });
    }

    private void asTheOwner(String sql, Object... args) {
        owner.thenRollBack(db -> db.update(sql, args));
    }

    /** Adds a row with {@code insert} (one parameter, returning the id), then runs {@code change} on that id; rolled back. */
    private void asTheApplicationOn(String insert, String value, String change) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update(change, jdbc.queryForObject(insert, Integer.class, value));
        });
    }

    private void asTheOwnerOn(String insert, String value, String change) {
        owner.thenRollBack(db -> db.update(change, db.queryForObject(insert, Integer.class, value)));
    }

    private static void assertRefused(String sqlState, Executable attempt) {
        DataAccessException refused = assertThrows(DataAccessException.class, attempt);
        SQLException cause = assertInstanceOf(SQLException.class, refused.getMostSpecificCause());
        assertEquals(sqlState, cause.getSQLState(), cause.getMessage());
    }

    private void assertTheEventIsUntouched() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE request_id=? AND NOT archived "
            + "AND details = '{\"side\": \"BUY\"}'::jsonb", Integer.class, requestId));
    }
}
