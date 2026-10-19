package com.lemarketjames.holdings.service;

import com.lemarketjames.holdings.dto.SettlementRequest;

/**
 * A point inside the settlement transaction: everything a settlement writes has been written, and
 * nothing has been committed yet. An exception thrown from here rolls the whole settlement back.
 *
 * <p>It exists so {@link HoldingsSettlementService} does not know about the test control that uses
 * it ({@link SettlementFaults}); the service only promises to pass through this point.
 */
public interface SettlementCheckpoint {

    /**
     * Called once for every settlement that is applied, never for one that is refused.
     *
     * @param request the fill being settled
     */
    void beforeCommit(SettlementRequest request);
}
