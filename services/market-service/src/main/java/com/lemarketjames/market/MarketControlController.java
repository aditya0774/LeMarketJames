package com.lemarketjames.market;

import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketFeedControl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Test controls for the simulated quote feed (contract C4): set a stock's price, or make quotes
 * stale or unavailable on demand, so pricing and failure handling can be tested without a live
 * provider.
 *
 * <p>Only exists when {@code sim.control.enabled=true} (test environments), and lives under
 * /internal, which the gateway never routes: like holdings' settlement endpoint, it is reachable
 * only by calling market-service directly on its own port.
 */
@RestController
@RequestMapping("/internal/market/control")
@ConditionalOnProperty(prefix = "sim.control", name = "enabled", havingValue = "true")
public class MarketControlController {

    private final MarketFeedControl feed;

    public MarketControlController(MarketFeedControl feed) {
        this.feed = feed;
    }

    /** The current feed mode and pinned tickers. */
    @GetMapping
    public ControlState state() {
        return new ControlState(feed.feedMode(), feed.pinnedTickers());
    }

    /** Body: {@code {"mode": "LIVE" | "STALE" | "UNAVAILABLE"}}. */
    @PutMapping("/feed")
    public ControlState setFeed(@RequestBody FeedRequest request) {
        feed.setFeedMode(request.mode());
        return state();
    }

    /** Body: {@code {"price": 123.45, "pinned": true}}; pinned defaults to true. */
    @PutMapping("/prices/{ticker}")
    public ControlState setPrice(@PathVariable String ticker, @RequestBody PriceRequest request) {
        feed.setPrice(ticker, request.price(), request.pinned() == null || request.pinned());
        return state();
    }

    /** Lets a pinned stock move again. */
    @DeleteMapping("/prices/{ticker}")
    public ControlState releasePrice(@PathVariable String ticker) {
        feed.releasePrice(ticker);
        return state();
    }

    /** LIVE feed, nothing pinned. */
    @PostMapping("/reset")
    public ControlState reset() {
        feed.reset();
        return state();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    public record ControlState(FeedMode feedMode, Set<String> pinned) {
    }

    public record FeedRequest(FeedMode mode) {
    }

    public record PriceRequest(double price, Boolean pinned) {
    }
}
