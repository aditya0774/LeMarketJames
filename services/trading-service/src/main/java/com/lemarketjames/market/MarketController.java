package com.lemarketjames.market;

import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.MarketFeedControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The market's HTTP surface for callers in other services: orders-service reads prices here,
 * through libs/market-client's MarketDataClient, while this service's own features use
 * {@link MarketDataService} in-process. Returns {@link QuoteSnapshot} directly since callers share
 * this exact record via libs/market-client.
 *
 * <p>While the feed is {@link FeedMode#UNAVAILABLE} (contract C4), every endpoint answers 503, which
 * callers' MarketDataClient already treats as "no quote".
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketData;
    private final MarketFeedControl feed;

    public MarketController(MarketDataService marketData, MarketFeedControl feed) {
        this.marketData = marketData;
        this.feed = feed;
    }

    @GetMapping("/quotes/{ticker}")
    public ResponseEntity<QuoteSnapshot> getByTicker(@PathVariable String ticker) {
        return quote(() -> marketData.findByTicker(ticker));
    }

    @GetMapping("/quotes/by-instrument/{instrumentId}")
    public ResponseEntity<QuoteSnapshot> getByInstrumentId(@PathVariable int instrumentId) {
        return quote(() -> marketData.findByInstrumentId(instrumentId));
    }

    @GetMapping("/quotes")
    public ResponseEntity<Collection<QuoteSnapshot>> getAll() {
        if (feed.feedMode() == FeedMode.UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(marketData.findAll());
    }

    private ResponseEntity<QuoteSnapshot> quote(Supplier<Optional<QuoteSnapshot>> lookup) {
        if (feed.feedMode() == FeedMode.UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return lookup.get()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
