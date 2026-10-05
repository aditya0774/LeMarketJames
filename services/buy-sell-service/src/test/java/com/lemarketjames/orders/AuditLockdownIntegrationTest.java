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
 * trigger refuses everyone else, the owner included.
 *
 * <p>PostgreSQL only: accounts, grants and triggers are the database's, so the H2 unit-test schema
 * has none of them. Every attempt runs in a transaction that is rolled back whatever happens, so
 * one that wrongly got through still could not damage the database it ran against.
 */
@SpringBootTest
class AuditLockdownIntegrationTest {

    /** PostgreSQL's own code for a missing privilege. */
    static final String PERMISSION_DENIED = "42501";
    /** Raised by the triggers in 016. */
    static final String AUDIT_IMMUTABLE = "LM001";

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
        assertEquals(List.of("A", "A", "A", "A"), jdbc.queryForList(
            "SELECT tgenabled::text FROM pg_trigger WHERE tgname IN ('audit_log_immutable_row', "
                + "'audit_log_immutable_truncate', 'order_events_immutable_row', 'order_events_immutable_truncate')",
            String.class));
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
