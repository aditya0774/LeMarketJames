package com.lemarketjames.orders.execution;

import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.holdings.client.SettlementRejectedException;
import org.springframework.stereotype.Service;

/**
 * Coordinates execution in two phases: persist intent first, then call holdings.
 * No local transaction spans HTTP: holdings' audit foreign keys must not wait on our order lock.
 */
@Service
public class OrderExecutionService {
    private final OrderExecutionTransactions transactions;
    private final HoldingsSettlementClient settlement;
    public OrderExecutionService(OrderExecutionTransactions transactions, HoldingsSettlementClient settlement) {
        this.transactions = transactions; this.settlement = settlement;
    }

    /**
     * Runs one execution attempt.
     *
     * <p>Definite settlement refusals are finalized immediately. Unknown failures are intentionally
     * left unresolved so the persisted intent is retried by the scheduler or after restart.
     */
    public void execute(int id, boolean manual) {
        var intent = transactions.prepare(id, manual);
        if (intent == null) return;
        try {
            settlement.settle(intent);
        } catch (SettlementRejectedException rejected) {
            transactions.finish(id, rejected.getReason());
            return;
        }
        // A successful settle response can now finalize the persisted intent.
        transactions.finish(id, null);
    }
}
