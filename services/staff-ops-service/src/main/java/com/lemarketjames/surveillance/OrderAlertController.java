package com.lemarketjames.surveillance;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The alerts Trading Operations read (contract C6). Role enforcement is in SecurityConfig; this
 * service is reached through the staff app only.
 */
@RestController
@RequestMapping("/api/v1/surveillance")
public class OrderAlertController {

    private final SurveillanceService surveillance;

    public OrderAlertController(SurveillanceService surveillance) {
        this.surveillance = surveillance;
    }

    /**
     * @param reason only alerts raised for this {@link AlertReason}; an unknown name is answered with 400
     * @param from   first UTC day, included
     * @param to     last UTC day, included; before {@code from} is answered with 400
     * @return the newest matching alerts, newest first, with the quantity at which an order counts as large
     */
    @GetMapping("/alerts")
    public AlertList alerts(@RequestParam(required = false) AlertReason reason,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AlertFilter filter = new AlertFilter(reason, from, to);
        return new AlertList(true, surveillance.largeOrderQuantity(), surveillance.latest(filter).stream()
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
     * @param alertId         this alert
     * @param orderId         the order it is about
     * @param accountId       the order's account
     * @param instrumentId    the stock ordered
     * @param side            BUY or SELL
     * @param quantity        shares ordered
     * @param price           price per share the order was placed at; null for a SELL
     * @param reason          why the alert was raised, an {@link AlertReason} name
     * @param submittedAt     when the order was placed (UTC)
     * @param rejectionReason why the order was rejected, for an alert about a rejection; otherwise null
     * @param occurredAt      when what the alert is about happened (UTC); the list is newest first by it
     */
    public record AlertView(Integer alertId, Integer orderId, Integer accountId, Integer instrumentId, String side,
                            BigDecimal quantity, BigDecimal price, AlertReason reason, Instant submittedAt,
                            String rejectionReason, Instant occurredAt) {

        static AlertView of(OrderAlert alert) {
            return new AlertView(alert.getAlertId(), alert.getOrderId(), alert.getAccountId(),
                alert.getInstrumentId(), alert.getSide(), alert.getQuantity(), alert.getPrice(), alert.getReason(),
                alert.getSubmittedAt(), alert.getRejectionReason(), alert.getOccurredAt());
        }
    }
}
