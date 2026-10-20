package com.lemarketjames.market.inprocess;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketFeedControl;
import com.lemarketjames.market.service.PriceHistoryReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Charts go empty during a feed outage (contract C4), as they did when history came over HTTP. */
class InProcessPriceHistoryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 2);

    private final PriceHistoryReader reader = mock(PriceHistoryReader.class);
    private final MarketFeedControl feed = mock(MarketFeedControl.class);
    private final InProcessPriceHistory history = new InProcessPriceHistory(reader, feed);

    private final List<DailyBar> bars = List.of(new DailyBar(FROM, 1, 2, 0.5, 1.5, 100));
    private final List<PricePoint> points = List.of(new PricePoint(Instant.EPOCH, 1.5));

    @Test
    void aLiveFeedAnswersWithTheStoredHistory() {
        when(feed.feedMode()).thenReturn(FeedMode.LIVE);
        when(reader.dailyBars(1, FROM)).thenReturn(bars);
        when(reader.intraday(1)).thenReturn(points);

        assertEquals(bars, history.dailyBars(1, FROM));
        assertEquals(points, history.intraday(1));
    }

    @Test
    void anUnavailableFeedAnswersNoHistory() {
        when(feed.feedMode()).thenReturn(FeedMode.UNAVAILABLE);
        when(reader.dailyBars(1, FROM)).thenReturn(bars);
        when(reader.intraday(1)).thenReturn(points);

        assertTrue(history.dailyBars(1, FROM).isEmpty());
        assertTrue(history.intraday(1).isEmpty());
    }
}
