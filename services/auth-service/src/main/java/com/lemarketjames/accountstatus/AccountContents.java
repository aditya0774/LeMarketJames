package com.lemarketjames.accountstatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What a client's trading account still holds, for deciding whether it may be closed (BR-03).
 * An interface so the closing rules can be tested without the holdings and orders tables, which
 * belong to other services.
 */
public interface AccountContents {

    /** The account of the client with this username; empty if the client has no account. */
    Optional<Contents> of(String username);

    /**
     * @param cashBalance cash left on the account; it stays there after closing
     * @param holdings    how many stocks the account holds any shares of
     * @param openOrders  how many of its orders are not yet final
     */
    record Contents(BigDecimal cashBalance, String currency, long holdings, long openOrders) {

        /** Closing is allowed only with nothing left that could still move. Cash may remain. */
        @JsonProperty("closable")
        public boolean closable() {
            return holdings == 0 && openOrders == 0;
        }
    }
}
