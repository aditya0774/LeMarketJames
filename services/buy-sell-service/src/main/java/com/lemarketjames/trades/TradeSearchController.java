package com.lemarketjames.trades;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeSearchController {
    private final TradeSearchService trades;

    public TradeSearchController(TradeSearchService trades) {
        this.trades = trades;
    }

    @GetMapping("/api/v1/orders/trades/search")
    public List<TradeSearchResult> search(@RequestParam(required = false) String orderId,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return trades.search(parseId(orderId), parseId(clientId), from, to);
    }

    private Integer parseId(String value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("IDs must be positive integers", ex);
        }
    }
}
