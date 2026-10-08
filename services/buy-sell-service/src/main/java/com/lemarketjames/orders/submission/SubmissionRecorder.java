package com.lemarketjames.orders.submission;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.audit.SubmissionAuditEvent;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.events.OrderSubmitted;
import com.lemarketjames.orders.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Commits the outcome of an order submission (contract C2). A separate bean from the coordinator
 * in {@code OrderService} so each outcome is exactly one transaction: an accepted order is saved
 * together with its trail, and a refused order's trail commits on its own, with no order
 * transaction around it to roll it back.
 */
@Service
public class SubmissionRecorder {

    private final OrderRepository orders;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;

    public SubmissionRecorder(OrderRepository orders, AuditRecorder audit, ApplicationEventPublisher events) {
        this.orders = orders;
        this.audit = audit;
        this.events = events;
    }

    /**
     * Saves an order that passed its checks: SUBMITTED, one RULE_CHECKED per rule, then VALIDATED.
     * The new order is also announced with an {@link OrderSubmitted} (contract C6).
     */
    @Transactional
    public Order accept(Order order, SubmissionTrail trail) {
        Order saved = orders.save(order);
        Integer orderId = saved.getOrderId();
        recordSubmittedAndChecks(trail, orderId);
        record(trail, AuditEventType.VALIDATED, null, orderId, Map.of("checks",
            trail.checks().stream().map(check -> check.rule().name()).toList()));
        // Published inside this transaction, so listeners that wait for the commit never hear of an
        // order whose save was rolled back.
        events.publishEvent(new OrderSubmitted(orderId, saved.getAccountId(), saved.getInstrumentId(),
            saved.getOrderType(), saved.getQuantity(), saved.getPricePerUnit(), Instant.now()));
        return saved;
    }

    /** Records a refused submission: SUBMITTED, then each check that ran, the last one not a PASS. */
    @Transactional
    public void refuse(SubmissionTrail trail) {
        recordSubmittedAndChecks(trail, null);
    }

    private void recordSubmittedAndChecks(SubmissionTrail trail, Integer orderId) {
        // HashMap because a SELL, or a BUY refused before it was priced, has a null price.
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("side", trail.side().name());
        submitted.put("quantity", trail.quantity());
        submitted.put("price", trail.price());
        submitted.put("instrumentId", trail.instrumentId());
        if (!trail.ownsAccount()) {
            // The account column stays empty for an account that isn't the caller's; keep what was asked for.
            submitted.put("requestedAccountId", trail.requestedAccountId());
        }
        record(trail, AuditEventType.SUBMITTED, null, orderId, submitted);

        for (SubmissionTrail.Check check : trail.checks()) {
            Map<String, Object> details = new HashMap<>();
            details.put("rule", check.rule().name());
            details.put("result", check.result().name());
            if (check.reason() != null) {
                details.put("reason", check.reason());
            }
            record(trail, AuditEventType.RULE_CHECKED, check.rule().name(), orderId, details);
        }
    }

    private void record(SubmissionTrail trail, AuditEventType type, String rule, Integer orderId,
                        Map<String, Object> details) {
        audit.recordSubmission(new SubmissionAuditEvent(trail.requestId(), type, rule, orderId,
            trail.accountId(), trail.clientId(), details));
    }
}
