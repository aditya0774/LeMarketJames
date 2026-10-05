package com.lemarketjames.trades;

import com.lemarketjames.orders.entity.Order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Stored execution details for operations; searching never requires a live market quote. */
public record TradeSearchResult(Integer orderId, Integer clientId, Integer accountId,
        Integer instrumentId, String symbol, OrderType side, BigDecimal quantity,
        BigDecimal pricePerUnit, LocalDateTime filledAt) {}
