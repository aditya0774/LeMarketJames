package com.lemarketjames.trades;

import com.lemarketjames.trades.dto.TradeDto;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/v1/trades?accountId=... — completed (FILLED) orders, ownership-scoped like holdings. */
@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @GetMapping
    public List<TradeDto> getTradeHistory(@RequestParam Integer accountId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return tradeService.getTradeHistory(accountId, username);
    }
}
