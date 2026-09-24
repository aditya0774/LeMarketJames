package com.lemarketjames.market;

import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * The first HTTP surface for the market feature: server-to-server callers (core-service today)
 * reach {@link MarketDataService} over the network instead of as an in-process bean. Returns
 * {@link QuoteSnapshot} directly since callers share this exact record via libs/market-client.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketData;

    public MarketController(MarketDataService marketData) {
        this.marketData = marketData;
    }

    @GetMapping("/quotes/{ticker}")
    public ResponseEntity<QuoteSnapshot> getByTicker(@PathVariable String ticker) {
        return marketData.findByTicker(ticker)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/quotes/by-instrument/{instrumentId}")
    public ResponseEntity<QuoteSnapshot> getByInstrumentId(@PathVariable int instrumentId) {
        return marketData.findByInstrumentId(instrumentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/quotes")
    public Collection<QuoteSnapshot> getAll() {
        return marketData.findAll();
    }
}
