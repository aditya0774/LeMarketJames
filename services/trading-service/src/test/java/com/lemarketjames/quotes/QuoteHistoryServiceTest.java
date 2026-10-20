package com.lemarketjames.quotes;

import com.lemarketjames.market.client.MarketFeedStatus;
import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.PriceHistoryService;
import com.lemarketjames.quotes.dto.ChartPointDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteHistoryServiceTest {

    private static final MarketInstrument AAPL = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 1.5, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

    private final MarketDataService marketData = mock(MarketDataService.class);
    private final PriceHistoryService priceHistory = mock(PriceHistoryService.class);
    private final MarketFeedStatus feedStatus = new MarketFeedStatus();
    private final QuoteHistoryService service = new QuoteHistoryService(marketData, priceHistory, feedStatus);

    @Test
    void oneDayIsTodaysMinutePrices() {
        when(marketData.findByTicker("AAPL")).thenReturn(Optional.of(quote()));
        when(priceHistory.intraday(1)).thenReturn(List.of(new PricePoint(NOW, 231.456)));

        assertEquals(List.of(new ChartPointDto(NOW, new BigDecimal("231.46"))),
                service.history(" aapl ", ChartRange.ONE_DAY));
    }

    @Test
    void longerRangesAreDailyClosesStampedAtTheClosingBell() {
        when(marketData.findByTicker("AAPL")).thenReturn(Optional.of(quote()));
        when(priceHistory.dailyBars(1, LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(new DailyBar(LocalDate.of(2026, 9, 15), 1, 2, 0.5, 225.1, 10)));

        // 16:00 in New York on 15 September (EDT, UTC-4) is 20:00 UTC.
        assertEquals(List.of(new ChartPointDto(Instant.parse("2026-09-15T20:00:00Z"), new BigDecimal("225.10"))),
                service.history("AAPL", ChartRange.ONE_MONTH));
    }

    @Test
    void aQuoteWithoutATradingDateCountsBackFromTodaysDate() {
        QuoteSnapshot undated = new QuoteSnapshot(AAPL, 231.0, 230.9, 231.1, 229.0, 232.0, 228.5, 228.0, 9_000L, NOW, null);
        when(marketData.findByTicker("AAPL")).thenReturn(Optional.of(undated));
        when(priceHistory.dailyBars(1, LocalDate.now().minusWeeks(1))).thenReturn(List.of());

        assertEquals(List.of(), service.history("AAPL", ChartRange.ONE_WEEK));
    }

    @Test
    void aMissingSymbolIsNotFound() {
        when(marketData.findByTicker("")).thenReturn(Optional.empty());

        assertThrows(QuoteService.SymbolNotFoundException.class, () -> service.history(null, ChartRange.ONE_DAY));
    }

    @Test
    void anUnknownSymbolIsNotFoundAndAnOutageIsUnavailable() {
        when(marketData.findByTicker("NOPE")).thenReturn(Optional.empty());

        assertThrows(QuoteService.SymbolNotFoundException.class, () -> service.history("NOPE", ChartRange.ONE_DAY));
        feedStatus.recordFailure();
        assertThrows(QuoteService.QuoteFeedUnavailableException.class,
                () -> service.history("NOPE", ChartRange.ONE_DAY));
    }

    @Test
    void intradayForAllIsEveryStocksDayBySymbol() {
        when(marketData.findAll()).thenReturn(List.of(quote()));
        when(priceHistory.intraday(1)).thenReturn(List.of(new PricePoint(NOW.minusSeconds(60), 230.004),
                new PricePoint(NOW, 231.456)));

        assertEquals(Map.of("AAPL", List.of(new BigDecimal("230.00"), new BigDecimal("231.46"))),
                service.intradayForAll());
    }

    @Test
    void intradayForAllIsUnavailableDuringAnOutage() {
        feedStatus.recordFailure();

        assertThrows(QuoteService.QuoteFeedUnavailableException.class, service::intradayForAll);
    }

    private static QuoteSnapshot quote() {
        return new QuoteSnapshot(AAPL, 231.0, 230.9, 231.1, 229.0, 232.0, 228.5, 228.0, 9_000L, NOW, TODAY);
    }
}
