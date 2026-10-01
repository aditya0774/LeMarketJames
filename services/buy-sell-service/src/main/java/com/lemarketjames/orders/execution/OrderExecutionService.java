package com.lemarketjames.orders.execution;

import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.holdings.client.SettlementRejectedException;
import org.springframework.stereotype.Service;

/** No local transaction spans HTTP: holdings' audit foreign keys must not wait on our order lock. */
@Service
public class OrderExecutionService {
    private final OrderExecutionTransactions transactions;
    private final HoldingsSettlementClient settlement;
    public OrderExecutionService(OrderExecutionTransactions transactions, HoldingsSettlementClient settlement) {
        this.transactions = transactions; this.settlement = settlement;
    }
    public void execute(int id, boolean manual) {
        var intent = transactions.prepare(id, manual);
        if (intent == null) return;
        try {
            settlement.settle(intent);
        } catch (SettlementRejectedException rejected) {
            transactions.finish(id, rejected.getReason());
            return;
        }
        // Unknown/network failures leave the immutable intent pending for the next poll/restart.
        transactions.finish(id, null);
    }
}
