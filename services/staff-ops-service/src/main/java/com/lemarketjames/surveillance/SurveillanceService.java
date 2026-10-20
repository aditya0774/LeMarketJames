package com.lemarketjames.surveillance;

import com.lemarketjames.common.config.PlatformSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Decides which orders Trading Operations are alerted to, and reads the alerts back. There are
 * two rules: a new order that is large, and an order whose settlement was refused.
 *
 * <p>Kafka can deliver a record more than once, after a restart or a failed commit. Reviewing is
 * therefore idempotent: an order that already has its alert for a reason is left alone, so staff
 * never see the same alert twice.
 */
@Service
public class SurveillanceService {
    private static final Logger log = LoggerFactory.getLogger(SurveillanceService.class);

    /** How many alerts staff get back: the newest ones. */
    static final int LATEST = 100;

    /** The status of a rejected order, as the event carries it (contract C1). */
    static final String REJECTED = "REJECTED";

    /**
     * The rejection reasons that mean trading-service refused the settlement (contract C6,
     * "Automatic execution and recovery"): no cash left for a BUY, no shares left for a SELL.
     * They are names of orders-service's RejectionReason, kept as text because a consumer
     * depends on none of that service's classes.
     */
    static final Set<String> SETTLEMENT_REFUSALS = Set.of("INSUFFICIENT_CASH", "INSUFFICIENT_HOLDINGS");

    private final OrderAlertRepository alerts;
    private final PlatformSettings settings;

    public SurveillanceService(OrderAlertRepository alerts, PlatformSettings settings) {
        this.alerts = alerts;
        this.settings = settings;
    }

    /**
     * Raises an alert for a newly placed order if it is large (contract C5).
     *
     * @param order the order, as read from Kafka
     * @return whether an alert was raised; false for an order below the large-order quantity, one
     *         that already has its alert, or an event that lacks a field an alert needs
     */
    @Transactional
    public boolean review(OrderSubmittedMessage order) {
        if (!order.isComplete()) {
            log.warn("Kafka: skipped an incomplete new order: {}", order);
            return false;
        }
        if (order.quantity().compareTo(largeOrderQuantity()) < 0) {
            return false;
        }
        if (alerts.existsByOrderIdAndReason(order.orderId(), AlertReason.LARGE_ORDER)) {
            log.info("Kafka: order {} already has its alert", order.orderId());
            return false;
        }
        alerts.save(new OrderAlert(order, AlertReason.LARGE_ORDER));
        log.info("Alert raised: order={} account={} quantity={} reason={}", order.orderId(), order.accountId(),
            order.quantity(), AlertReason.LARGE_ORDER);
        return true;
    }

    /**
     * Raises an alert for an order that was rejected because its settlement was refused
     * (LMKT-71). Every other status change, and a rejection for any other reason, raises none.
     *
     * @param change the status change, as read from Kafka
     * @return whether an alert was raised; false for a change that is not a refused settlement,
     *         one that already has its alert, or an event that lacks a field an alert needs
     */
    @Transactional
    public boolean reviewStatusChange(OrderStatusChangedMessage change) {
        if (!change.isComplete()) {
            log.warn("Kafka: skipped an incomplete status change: {}", change);
            return false;
        }
        String why = change.rejectionReason();
        if (!REJECTED.equals(change.to()) || why == null || !SETTLEMENT_REFUSALS.contains(why)) {
            return false;
        }
        if (alerts.existsByOrderIdAndReason(change.orderId(), AlertReason.SETTLEMENT_REFUSED)) {
            log.info("Kafka: order {} already has its {} alert", change.orderId(), AlertReason.SETTLEMENT_REFUSED);
            return false;
        }
        alerts.save(new OrderAlert(change, AlertReason.SETTLEMENT_REFUSED));
        log.info("Alert raised: order={} account={} reason={} rejectionReason={}", change.orderId(),
            change.accountId(), AlertReason.SETTLEMENT_REFUSED, why);
        return true;
    }

    /** @return the newest alerts, newest first */
    @Transactional(readOnly = true)
    public List<OrderAlert> latest() {
        return alerts.findAllByOrderByOccurredAtDescAlertIdDesc(PageRequest.of(0, LATEST));
    }

    /** @return the number of shares at which an order counts as large in this environment */
    public BigDecimal largeOrderQuantity() {
        return settings.getSurveillance().getLargeOrderQuantity();
    }
}
