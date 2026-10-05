package com.lemarketjames.common.audit;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Contract C2: the audit event format and the same-transaction rule. */
class AuditRecorderTest {

    @Test
    void recordsTheEventWithTheAccountsClientAndUtcTime() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        AccountEntity account = new AccountEntity();
        account.setClientId(42);
        when(accounts.findById(7)).thenReturn(Optional.of(account));
        Instant now = Instant.parse("2026-09-16T15:00:00Z");

        new AuditRecorder(events, accounts, Clock.fixed(now, ZoneOffset.UTC))
                .record(AuditEventType.SUBMITTED, 99, 7, Map.of("side", "BUY"));

        ArgumentCaptor<AuditEventEntity> saved = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(events).save(saved.capture());
        assertEquals(99, saved.getValue().getOrderId());
        assertEquals(7, saved.getValue().getAccountId());
        assertEquals(42, saved.getValue().getClientId());
        assertEquals(AuditEventType.SUBMITTED, saved.getValue().getEventType());
        assertEquals(Map.of("side", "BUY"), saved.getValue().getDetails());
        assertEquals(now, saved.getValue().getOccurredAt());
    }

    // The rule "written in the same transaction as the change" is enforced by MANDATORY propagation:
    // Spring refuses a call that arrives without a transaction. Guard the annotation itself.
    @Test
    void recordRequiresAnExistingTransaction() throws NoSuchMethodException {
        Transactional transactional = AuditRecorder.class
                .getMethod("record", AuditEventType.class, Integer.class, Integer.class, Map.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    // A refused submission has no order and may have no account; the caller's client is given, not looked up.
    @Test
    void recordsASubmissionEventWithItsRequestIdAndUniqueKey() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Instant now = Instant.parse("2026-10-05T09:30:00Z");

        new AuditRecorder(events, accounts, Clock.fixed(now, ZoneOffset.UTC)).recordSubmission(
                new SubmissionAuditEvent("req-1", AuditEventType.RULE_CHECKED, "CASH", null, null, 42,
                        Map.of("rule", "CASH", "result", "FAIL", "reason", "INSUFFICIENT_CASH")));

        ArgumentCaptor<AuditEventEntity> saved = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(events).save(saved.capture());
        assertNull(saved.getValue().getOrderId());
        assertNull(saved.getValue().getAccountId());
        assertEquals(42, saved.getValue().getClientId());
        assertEquals(AuditEventType.RULE_CHECKED, saved.getValue().getEventType());
        assertEquals("req-1", saved.getValue().getRequestId());
        assertEquals("req-1:RULE_CHECKED:CASH", saved.getValue().getEventKey());
        assertEquals(now, saved.getValue().getOccurredAt());
        verifyNoInteractions(accounts);
    }

    @Test
    void theKeyHasNoRulePartForEventsThatAreNotAboutARule() {
        assertEquals("req-1:SUBMITTED",
                new SubmissionAuditEvent("req-1", AuditEventType.SUBMITTED, null, 9, 7, 42, Map.of()).eventKey());
    }

    @Test
    void recordSubmissionRequiresAnExistingTransaction() throws NoSuchMethodException {
        Transactional transactional = AuditRecorder.class
                .getMethod("recordSubmission", SubmissionAuditEvent.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }
}
