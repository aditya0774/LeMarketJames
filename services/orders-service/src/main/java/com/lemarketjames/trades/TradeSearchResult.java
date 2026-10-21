package com.lemarketjames.trades;

import com.lemarketjames.orders.entity.Order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stored execution details for operations; searching never requires a live market quote.
 * {@code archived}: the trade filled before the online retention window, so its audit trail is
 * archived (BR-14, contract C2). Archived trades are still found; they are never deleted.
 */
public record TradeSearchResult(Integer orderId, Integer clientId, Integer accountId,
        Integer instrumentId, String symbol, OrderType side, BigDecimal quantity,
        BigDecimal pricePerUnit, LocalDateTime filledAt, boolean archived) {

    /** The repository's projection; the service marks archived trades afterwards. */
    public TradeSearchResult(Integer orderId, Integer clientId, Integer accountId,
            Integer instrumentId, String symbol, OrderType side, BigDecimal quantity,
            BigDecimal pricePerUnit, LocalDateTime filledAt) {
        this(orderId, clientId, accountId, instrumentId, symbol, side, quantity, pricePerUnit, filledAt, false);
    }

    TradeSearchResult archived(boolean value) {
        return new TradeSearchResult(orderId, clientId, accountId, instrumentId, symbol, side, quantity,
                pricePerUnit, filledAt, value);
    }
}
