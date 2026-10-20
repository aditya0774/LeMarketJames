package com.lemarketjames.market;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketFeedControl;
import com.lemarketjames.market.service.PriceHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Past prices for charts, for callers in other services (this service's own charts read
 * {@code PriceHistoryService} in-process). Kept out of {@link MarketController} so current quotes and
 * history stay separate surfaces, mirroring the two client interfaces.
 *
 * <p>Like the quote endpoints, every endpoint answers 503 while the feed is
 * {@link FeedMode#UNAVAILABLE} (contract C4); an unknown instrument has an empty history.
 */
@RestController
@RequestMapping("/api/market")
public class PriceHistoryController {

    private final PriceHistoryService history;
    private final MarketFeedControl feed;

    public PriceHistoryController(PriceHistoryService history, MarketFeedControl feed) {
        this.history = history;
        this.feed = feed;
    }

    /** One bar per trading day from {@code from}, ending with today's so far. */
    @GetMapping("/history/{instrumentId}")
    public ResponseEntity<List<DailyBar>> dailyBars(
            @PathVariable int instrumentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return available(() -> history.dailyBars(instrumentId, from));
    }

    /** Today's minute-by-minute prices, ending with the live one. */
    @GetMapping("/intraday/{instrumentId}")
    public ResponseEntity<List<PricePoint>> intraday(@PathVariable int instrumentId) {
        return available(() -> history.intraday(instrumentId));
    }

    private <T> ResponseEntity<T> available(Supplier<T> read) {
        if (feed.feedMode() == FeedMode.UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(read.get());
    }
}
