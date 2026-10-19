package com.lemarketjames.market.inprocess;

import com.lemarketjames.market.client.MarketFeedStatus;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.MarketFeedControl;
import com.lemarketjames.market.service.MarketSimulator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The prices every feature of this service reads: the simulator's quotes, as a caller across the
 * network would see them.
 *
 * <p>Quotes, holdings and portfolio once reached the simulator over HTTP through market-client's
 * {@code MarketDataClient}, which is how they learned of a feed outage (contract C4): the
 * simulator's endpoints answer 503 while the feed is UNAVAILABLE, and the client then records the
 * outage in {@link MarketFeedStatus} and returns no quote. In this process there is no HTTP call
 * to fail, so this decorator does the same thing itself; without it those features would keep
 * showing prices during a simulated outage. {@code @Primary} so every {@code MarketDataService}
 * injection point gets it rather than the bare {@code MarketSimulator}.
 */
@Service
@Primary
public class InProcessMarketData implements MarketDataService {

    private final MarketDataService simulator;
    private final MarketFeedControl feed;
    private final MarketFeedStatus feedStatus;

    /**
     * @param simulator  the simulator's own quotes
     * @param feed       the feed mode the test controls set (contract C4)
     * @param feedStatus where callers look to tell an outage from an unknown ticker
     */
    public InProcessMarketData(MarketSimulator simulator, MarketFeedControl feed,
                               MarketFeedStatus feedStatus) {
        this.simulator = simulator;
        this.feed = feed;
        this.feedStatus = feedStatus;
    }

    @Override
    public Optional<QuoteSnapshot> findByTicker(String ticker) {
        return read(() -> simulator.findByTicker(ticker), Optional::empty);
    }

    @Override
    public Optional<QuoteSnapshot> findByInstrumentId(int instrumentId) {
        return read(() -> simulator.findByInstrumentId(instrumentId), Optional::empty);
    }

    @Override
    public Collection<QuoteSnapshot> findAll() {
        return read(simulator::findAll, List::of);
    }

    /** Answers like MarketDataClient: nothing, and an outage recorded, while the feed is down. */
    private <T> T read(Supplier<T> lookup, Supplier<T> unavailable) {
        if (feed.feedMode() == FeedMode.UNAVAILABLE) {
            feedStatus.recordFailure();
            return unavailable.get();
        }
        feedStatus.recordSuccess();
        return lookup.get();
    }
}
