package com.lemarketjames.market.inprocess;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketFeedControl;
import com.lemarketjames.market.service.PriceHistoryReader;
import com.lemarketjames.market.service.PriceHistoryService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * The price history every feature of this service reads, as a caller across the network would
 * see it: empty while the feed is UNAVAILABLE (contract C4), as market-client's
 * {@code PriceHistoryClient} returns when the history endpoints answer 503. The counterpart of
 * {@link InProcessMarketData} for charts; {@code @Primary} so injection points get it rather than
 * the bare {@link PriceHistoryReader}.
 */
@Service
@Primary
public class InProcessPriceHistory implements PriceHistoryService {

    private final PriceHistoryService reader;
    private final MarketFeedControl feed;

    /**
     * @param reader the stored history
     * @param feed   the feed mode the test controls set (contract C4)
     */
    public InProcessPriceHistory(PriceHistoryReader reader, MarketFeedControl feed) {
        this.reader = reader;
        this.feed = feed;
    }

    @Override
    public List<DailyBar> dailyBars(int instrumentId, LocalDate from) {
        return feed.feedMode() == FeedMode.UNAVAILABLE ? List.of() : reader.dailyBars(instrumentId, from);
    }

    @Override
    public List<PricePoint> intraday(int instrumentId) {
        return feed.feedMode() == FeedMode.UNAVAILABLE ? List.of() : reader.intraday(instrumentId);
    }
}
