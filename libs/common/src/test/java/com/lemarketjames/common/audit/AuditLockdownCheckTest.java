package com.lemarketjames.common.audit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contract C2: a service only starts on an account that can't change the audit trail. */
class AuditLockdownCheckTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditLockdownCheck check = new AuditLockdownCheck(jdbc);

    @Test
    void startsOnAnAccountThatCannotChangeAuditRecords() {
        connectedTo("PostgreSQL");
        when(jdbc.queryForList(AuditLockdownCheck.CHANGEABLE_AUDIT_TABLES, String.class)).thenReturn(List.of());

        assertDoesNotThrow(check::verify);
    }

    @Test
    void refusesToStartOnAnAccountThatCanChangeAuditRecords() {
        connectedTo("PostgreSQL");
        when(jdbc.queryForList(AuditLockdownCheck.CHANGEABLE_AUDIT_TABLES, String.class)).thenReturn(List.of("audit_log"));
        when(jdbc.queryForObject("SELECT current_user", String.class)).thenReturn("lemarket");

        IllegalStateException refused = assertThrows(IllegalStateException.class, check::verify);

        // Names the account and the table, so whoever reads the startup log knows what to fix.
        assertTrue(refused.getMessage().contains("'lemarket'"), refused.getMessage());
        assertTrue(refused.getMessage().contains("(audit_log)"), refused.getMessage());
    }

    // The unit tests run on H2, which has no accounts: the check must not get in their way.
    @Test
    void isSkippedOnADatabaseWithoutAccounts() {
        connectedTo("H2");

        assertDoesNotThrow(check::verify);

        verify(jdbc, never()).queryForList(anyString(), eq(String.class));
    }

    @Test
    void asksAboutBothAuditTablesAndEveryWayOfChangingThem() {
        assertTrue(AuditLockdownCheck.CHANGEABLE_AUDIT_TABLES.contains("ARRAY['audit_log', 'order_events']"));
        assertTrue(AuditLockdownCheck.CHANGEABLE_AUDIT_TABLES.contains("'UPDATE, DELETE, TRUNCATE'"));
    }

    @SuppressWarnings("unchecked")
    private void connectedTo(String databaseProduct) {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(databaseProduct);
    }
}
