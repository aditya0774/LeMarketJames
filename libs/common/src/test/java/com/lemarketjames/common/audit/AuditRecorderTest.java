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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
}
