package com.lemarketjames.orders.submission;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What happened during one order submission, collected in memory while the checks run and written
 * to the audit trail afterwards in a single transaction ({@link SubmissionRecorder}). Collecting
 * first is what lets a refused order's events commit without an order, and means a failure
 * part-way through a submission never leaves half a trail.
 */
public class SubmissionTrail {

    /** How a check ended. ERROR means it could not complete, e.g. trading-service was unreachable. */
    public enum Result { PASS, FAIL, ERROR }

    /**
     * One rule and how it ended.
     *
     * @param reason the code the caller was given, on FAIL; otherwise null
     */
    public record Check(ValidationRule rule, Result result, String reason) {}

    private final String requestId;
    private final Integer clientId;
    private final CreateOrderRequest request;
    private final List<Check> checks = new ArrayList<>();
    private ValidationRule open;
    private BigDecimal price;
    private BigDecimal shares;
    private boolean passed;

    /**
     * @param requestId the server-generated ID of this submission
     * @param clientId  the authenticated caller's client; null for a caller who isn't a client
     */
    public SubmissionTrail(String requestId, Integer clientId, CreateOrderRequest request) {
        this.requestId = requestId;
        this.clientId = clientId;
        this.request = request;
    }

    /** Starts a check; it stays open until {@link #pass()}, {@link #fail} or {@link #abandon()}. */
    public void begin(ValidationRule rule) {
        open = rule;
    }

    public void pass() {
        close(Result.PASS, null);
    }

    public void fail(String reason) {
        close(Result.FAIL, reason);
    }

    /** Ends a check that an unexpected error interrupted, so the trail shows where it stopped. */
    public void abandon() {
        if (open != null) {
            close(Result.ERROR, null);
        }
    }

    private void close(Result result, String reason) {
        checks.add(new Check(Objects.requireNonNull(open, "no check was started"), result, reason));
        open = null;
    }

    /** Marks every check as passed: what follows is saving the order, not checking it. */
    public void passedAll() {
        passed = true;
    }

    /** Whether every check passed, so a failure from here on is in saving the order, not a refusal. */
    public boolean hasPassedAll() {
        return passed;
    }

    /** The price the order was valued at, once the market has been asked. */
    public void priced(BigDecimal price) {
        this.price = price;
    }

    /** The whole shares the order is for, once the QUANTITY rule has passed (worked out from an amount, BR-04). */
    public void sized(BigDecimal shares) {
        this.shares = shares;
    }

    public String requestId() {
        return requestId;
    }

    public Integer clientId() {
        return clientId;
    }

    public List<Check> checks() {
        return Collections.unmodifiableList(checks);
    }

    public BigDecimal price() {
        return price;
    }

    /**
     * The account the order is for, once the caller is known to own it. Null until then, so an
     * event is never attributed to an account that isn't the caller's.
     */
    public Integer accountId() {
        return ownsAccount() ? request.getAccountId() : null;
    }

    public boolean ownsAccount() {
        return checks.contains(new Check(ValidationRule.ACCOUNT_ACCESS, Result.PASS, null));
    }

    public Integer requestedAccountId() {
        return request.getAccountId();
    }

    public Integer instrumentId() {
        return request.getInstrumentId();
    }

    public Order.OrderType side() {
        return request.getOrderType();
    }

    /** The shares the order is for: as asked for, or as worked out from its amount once that is known. */
    public BigDecimal quantity() {
        return shares != null ? shares : request.getQuantity();
    }

    /** The dollar amount the order was placed for; null for an order placed by shares. */
    public BigDecimal amount() {
        return request.getAmount();
    }
}
