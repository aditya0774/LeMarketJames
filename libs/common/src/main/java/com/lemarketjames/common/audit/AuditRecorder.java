package com.lemarketjames.common.audit;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * The only way to write the audit trail (contract C2). Each service records the lifecycle events
 * it causes, so audit never waits on another feature.
 *
 * <p>The rule "an event is written in the same transaction as the change it describes" is
 * enforced, not just documented: {@link Propagation#MANDATORY} makes a call from outside a
 * transaction fail, and inside one the event commits or rolls back together with the change.
 */
@Service
public class AuditRecorder {

    private final AuditEventRepository events;
    private final AccountRepository accounts;
    private final Clock clock;

    @Autowired
    public AuditRecorder(AuditEventRepository events, AccountRepository accounts) {
        this(events, accounts, Clock.systemUTC());
    }

    AuditRecorder(AuditEventRepository events, AccountRepository accounts, Clock clock) {
        this.events = events;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Records one event for an order.
     *
     * @param type      what happened
     * @param orderId   the order it happened to
     * @param accountId the order's account; the client id is resolved from it
     * @param details   the keys documented on {@code type}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEventType type, Integer orderId, Integer accountId, Map<String, Object> details) {
        Integer clientId = accounts.findById(accountId).map(AccountEntity::getClientId).orElse(null);
        events.save(new AuditEventEntity(orderId, accountId, clientId, type, details, clock.instant()));
    }

    /**
     * Records one event of an order submission. Its {@link SubmissionAuditEvent#eventKey()} is
     * unique in the table, so writing the same event again fails and rolls the caller's
     * transaction back instead of storing a duplicate.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSubmission(SubmissionAuditEvent event) {
        events.save(new AuditEventEntity(event, clock.instant()));
    }
}
