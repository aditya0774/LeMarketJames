package com.lemarketjames.surveillance;

import com.lemarketjames.common.config.PlatformSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Decides which new orders Trading Operations are alerted to, and reads the alerts back.
 *
 * <p>Kafka can deliver a record more than once, after a restart or a failed commit. Reviewing is
 * therefore idempotent: an order that already has its alert is left alone, so staff never see
 * the same order twice.
 */
@Service
public class SurveillanceService {
    private static final Logger log = LoggerFactory.getLogger(SurveillanceService.class);

    /** How many alerts staff get back: the newest ones. */
    static final int LATEST = 100;

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
        if (alerts.existsByOrderId(order.orderId())) {
            log.info("Kafka: order {} already has its alert", order.orderId());
            return false;
        }
        alerts.save(new OrderAlert(order, AlertReason.LARGE_ORDER));
        log.info("Alert raised: order={} account={} quantity={} reason={}", order.orderId(), order.accountId(),
            order.quantity(), AlertReason.LARGE_ORDER);
        return true;
    }

    /** @return the newest alerts, newest order first */
    @Transactional(readOnly = true)
    public List<OrderAlert> latest() {
        return alerts.findAllByOrderBySubmittedAtDescAlertIdDesc(PageRequest.of(0, LATEST));
    }

    /** @return the number of shares at which an order counts as large in this environment */
    public BigDecimal largeOrderQuantity() {
        return settings.getSurveillance().getLargeOrderQuantity();
    }
}
