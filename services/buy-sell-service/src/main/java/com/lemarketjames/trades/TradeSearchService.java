package com.lemarketjames.trades;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TradeSearchService {
    private final TradeSearchRepository trades;

    public TradeSearchService(TradeSearchRepository trades) {
        this.trades = trades;
    }

    public List<TradeSearchResult> search(Integer orderId, Integer clientId, String from, String to) {
        if (orderId != null) {
            if (orderId <= 0 || clientId != null || from != null || to != null) {
                throw new IllegalArgumentException("Supply a positive orderId alone, or clientId with from and to");
            }
            return trades.findTrade(orderId, OrderStatus.FILLED);
        }
        if (clientId == null || clientId <= 0 || from == null || to == null) {
            throw new IllegalArgumentException("Supply a positive clientId with both from and to, or orderId alone");
        }
        LocalDate start = parseDate(from);
        LocalDate end = parseDate(to);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        // Stored timestamps are UTC. Exclusive next midnight includes the entire final day.
        return trades.findClientTrades(clientId, start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                OrderStatus.FILLED);
    }

    private LocalDate parseDate(String value) {
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new IllegalArgumentException("Dates must be YYYY-MM-DD");
            }
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw new IllegalArgumentException("Date year must be positive");
            return date;
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid calendar date", ex);
        }
    }
}
