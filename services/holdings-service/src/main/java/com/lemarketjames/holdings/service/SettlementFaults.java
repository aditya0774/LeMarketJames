package com.lemarketjames.holdings.service;

import com.lemarketjames.holdings.dto.SettlementRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes settlements fail part-way, on purpose, for the accounts a test has named. It is how the
 * end-to-end tests prove on a running stack that a failed settlement changes nothing and settles
 * once when retried (S17 and S18, AC2).
 *
 * <p>No account is named unless a test names one through {@code SettlementControlController},
 * which exists only where {@code lmj.settlement.control-enabled} is on (contract C5). Everywhere
 * else this does nothing. The failure is per account, so a test only ever breaks its own orders.
 */
@Component
public class SettlementFaults implements SettlementCheckpoint {

    private final EntityManager entityManager;

    /** The accounts whose settlements fail, each with how many have failed since it was named. */
    private final Map<Integer, AtomicInteger> failuresByAccount = new ConcurrentHashMap<>();

    public SettlementFaults(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** From now on every settlement for this account fails. Naming it again keeps its count. */
    public void failFor(int accountId) {
        failuresByAccount.putIfAbsent(accountId, new AtomicInteger());
    }

    /** Settlements for this account work again, and its count is forgotten. */
    public void restore(int accountId) {
        failuresByAccount.remove(accountId);
    }

    public boolean failsFor(int accountId) {
        return failuresByAccount.containsKey(accountId);
    }

    /** @return the settlements failed for this account since it was named; 0 if it is not named */
    public int failuresOf(int accountId) {
        AtomicInteger failures = failuresByAccount.get(accountId);
        return failures == null ? 0 : failures.get();
    }

    /**
     * Fails the settlement of a named account. It flushes first, so the cash movement, the holding,
     * the receipt and the audit event have really been sent to the database: what the caller then
     * sees undone is a rollback, not writes that were never made.
     */
    @Override
    public void beforeCommit(SettlementRequest request) {
        AtomicInteger failures = failuresByAccount.get(request.getAccountId());
        if (failures == null) {
            return;
        }
        entityManager.flush();
        failures.incrementAndGet();
        // Not an IllegalArgumentException: that would be answered as a 400. This one is a 500,
        // which buy-sell-service treats as an unknown failure and retries (contract C6).
        throw new IllegalStateException("Settlement of order " + request.getOrderId()
            + " failed on purpose (settlement test control)");
    }
}
