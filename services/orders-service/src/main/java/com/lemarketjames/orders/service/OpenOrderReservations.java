package com.lemarketjames.orders.service;

import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.Order.OrderType;
import com.lemarketjames.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * What an account's open orders have already promised (BR-05): cash for open BUYs and shares for
 * open SELLs. Settlement only moves cash and shares when an order fills, so without this a client
 * could place several orders that each fit the balance but together exceed it.
 *
 * <p>An order counts while it is open ({@link OrderStatus#isOpen()}), including the moment between
 * its settlement and its FILLED step, when the cash or shares have already moved. Counting it twice
 * for that instant can only refuse an order, never let one overspend; settlement rechecks anyway.
 */
@Service
public class OpenOrderReservations {

    private static final List<OrderStatus> OPEN = Arrays.stream(OrderStatus.values())
        .filter(OrderStatus::isOpen).toList();

    private final OrderRepository orders;

    public OpenOrderReservations(OrderRepository orders) {
        this.orders = orders;
    }

    /**
     * Cash held for the account's open BUYs: quantity × the ask each was placed at. The same sum as
     * trading-service's {@code buyingPower} (cash − this), so the client is refused exactly when an
     * order costs more than the buying power they see.
     */
    public BigDecimal reservedCash(int accountId) {
        return orders.findByAccountIdAndOrderTypeAndOrderStatusIn(accountId, OrderType.BUY, OPEN).stream()
            // Every BUY is priced at placement; a missing price counts as zero, as in buyingPower.
            .map(order -> order.getQuantity().multiply(
                order.getPricePerUnit() == null ? BigDecimal.ZERO : order.getPricePerUnit()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Shares of one stock already promised to the account's open SELLs. */
    public BigDecimal reservedShares(int accountId, int instrumentId) {
        return orders.findByAccountIdAndInstrumentIdAndOrderTypeAndOrderStatusIn(
                accountId, instrumentId, OrderType.SELL, OPEN).stream()
            .map(Order::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
