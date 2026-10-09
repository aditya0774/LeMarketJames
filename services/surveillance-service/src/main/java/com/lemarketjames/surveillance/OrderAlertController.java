package com.lemarketjames.surveillance;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The alerts Trading Operations read (contract C6). Role enforcement is in SecurityConfig; this
 * service is reached through the staff gateway only.
 */
@RestController
@RequestMapping("/api/v1/surveillance")
public class OrderAlertController {

    private final SurveillanceService surveillance;

    public OrderAlertController(SurveillanceService surveillance) {
        this.surveillance = surveillance;
    }

    /** @return the newest alerts, newest order first, with the quantity that made them large */
    @GetMapping("/alerts")
    public AlertList alerts() {
        return new AlertList(true, surveillance.largeOrderQuantity(), surveillance.latest().stream()
            .map(AlertView::of)
            .toList());
    }

    /**
     * The response body.
     *
     * @param success            always true
     * @param largeOrderQuantity shares at which an order counts as large in this environment
     * @param alerts             the alerts
     */
    public record AlertList(boolean success, BigDecimal largeOrderQuantity, List<AlertView> alerts) {
    }

    /**
     * One alert as staff see it.
     *
     * @param alertId      this alert
     * @param orderId      the order it is about
     * @param accountId    the order's account
     * @param instrumentId the stock ordered
     * @param side         BUY or SELL
     * @param quantity     shares ordered
     * @param price        price per share the order was placed at; null for a SELL
     * @param reason       why the alert was raised, an {@link AlertReason} name
     * @param submittedAt  when the order was placed (UTC)
     */
    public record AlertView(Integer alertId, Integer orderId, Integer accountId, Integer instrumentId, String side,
                            BigDecimal quantity, BigDecimal price, AlertReason reason, Instant submittedAt) {

        static AlertView of(OrderAlert alert) {
            return new AlertView(alert.getAlertId(), alert.getOrderId(), alert.getAccountId(),
                alert.getInstrumentId(), alert.getSide(), alert.getQuantity(), alert.getPrice(), alert.getReason(),
                alert.getSubmittedAt());
        }
    }
}
