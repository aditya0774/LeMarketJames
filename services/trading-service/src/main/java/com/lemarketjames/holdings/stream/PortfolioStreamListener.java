package com.lemarketjames.holdings.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards a settlement to the account's portfolio streams only after it has committed, so a
 * settlement that rolls back never tells the browser about cash it doesn't have.
 */
@Component
public class PortfolioStreamListener {

    private final PortfolioStreamService streams;

    public PortfolioStreamListener(PortfolioStreamService streams) {
        this.streams = streams;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBalanceChanged(BalanceChanged event) {
        streams.publish(event);
    }
}
