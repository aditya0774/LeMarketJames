package com.lemarketjames.market.inprocess;

import com.lemarketjames.market.client.MarketFeedStatus;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A feed outage (contract C4) reaches in-process callers the way it reached HTTP callers. */
class InProcessMarketDataTest {

    private final MarketSimulator simulator = mock(MarketSimulator.class);
    private final QuoteSnapshot quote = mock(QuoteSnapshot.class);
    private MarketFeedStatus feedStatus;
    private InProcessMarketData marketData;

    @BeforeEach
    void setUp() {
        feedStatus = new MarketFeedStatus();
        marketData = new InProcessMarketData(simulator, simulator, feedStatus);
        when(simulator.findByTicker("AAPL")).thenReturn(Optional.of(quote));
        when(simulator.findByInstrumentId(1)).thenReturn(Optional.of(quote));
        when(simulator.findAll()).thenReturn(List.of(quote));
    }

    @Test
    void aLiveFeedAnswersWithTheSimulatorsQuotes() {
        when(simulator.feedMode()).thenReturn(FeedMode.LIVE);

        assertEquals(Optional.of(quote), marketData.findByTicker("AAPL"));
        assertEquals(Optional.of(quote), marketData.findByInstrumentId(1));
        assertEquals(List.of(quote), List.copyOf(marketData.findAll()));
        assertTrue(feedStatus.isAvailable());
    }

    @Test
    void anUnavailableFeedAnswersNothingAndIsRecordedAsAnOutage() {
        when(simulator.feedMode()).thenReturn(FeedMode.UNAVAILABLE);

        assertTrue(marketData.findByTicker("AAPL").isEmpty());
        assertTrue(marketData.findByInstrumentId(1).isEmpty());
        assertTrue(marketData.findAll().isEmpty());
        assertFalse(feedStatus.isAvailable());
        verify(simulator, never()).findByTicker("AAPL");
    }

    @Test
    void theOutageEndsWithTheFirstReadOnceTheFeedIsBack() {
        when(simulator.feedMode()).thenReturn(FeedMode.UNAVAILABLE, FeedMode.LIVE);

        marketData.findByTicker("AAPL");
        assertFalse(feedStatus.isAvailable());

        assertEquals(Optional.of(quote), marketData.findByTicker("AAPL"));
        assertTrue(feedStatus.isAvailable());
    }
}
