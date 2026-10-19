package com.lemarketjames.activity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Market activity for anyone who is signed in (contract C6): how much of each stock traded lately. */
@RestController
@RequestMapping("/api/v1/market-activity")
public class MarketActivityController {

    private final MarketActivityService activity;

    public MarketActivityController(MarketActivityService activity) {
        this.activity = activity;
    }

    /** @return the traded volume of every stock that traded in the window ending now */
    @GetMapping
    public MarketActivity latest() {
        Instant since = activity.windowStart();
        return new MarketActivity(true, MarketActivityService.WINDOW.toHours(), since, activity.activitySince(since));
    }

    /**
     * The response body.
     *
     * @param success     always true
     * @param windowHours how many hours back the figures look
     * @param since       the start of that window (UTC)
     * @param activity    one entry per stock that traded in it; a stock that did not trade has none
     */
    public record MarketActivity(boolean success, long windowHours, Instant since, List<InstrumentActivity> activity) {
    }
}
