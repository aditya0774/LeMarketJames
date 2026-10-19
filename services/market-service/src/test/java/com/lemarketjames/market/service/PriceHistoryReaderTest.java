package com.lemarketjames.market.service;

import com.lemarketjames.market.entity.PriceCandleEntity;
import com.lemarketjames.market.entity.PriceHistoryEntity;
import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.repository.PriceCandleRepository;
import com.lemarketjames.market.repository.PriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Stored history plus the live quote, as charts read it. */
@ExtendWith(MockitoExtension.class)
class PriceHistoryReaderTest {

    private static final MarketInstrument AAPL = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final QuoteSnapshot LIVE = new QuoteSnapshot(AAPL, 231.0, 230.9, 231.1, 229.0, 232.0, 228.5,
            228.0, 9_000L, NOW, TODAY);

    @Mock
    private PriceHistoryRepository historyRepository;

    @Mock
    private PriceCandleRepository candleRepository;

    @Mock
    private MarketDataService marketData;

    @InjectMocks
    private PriceHistoryReader reader;

    @Test
    void dailyBarsEndWithTheLiveQuoteInPlaceOfTodaysStoredBar() {
        LocalDate from = LocalDate.of(2026, 10, 13);
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(LIVE));
        when(historyRepository.findByInstrumentIdAndTradingDateGreaterThanEqualOrderByTradingDate(1, from))
                .thenReturn(List.of(bar(LocalDate.of(2026, 10, 13), "225"), bar(LocalDate.of(2026, 10, 14), "228"),
                        bar(TODAY, "230")));

        List<DailyBar> bars = reader.dailyBars(1, from);

        assertEquals(List.of(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), TODAY),
                bars.stream().map(DailyBar::date).toList());
        assertEquals(228.0, bars.get(1).close());
        assertEquals(new DailyBar(TODAY, 229.0, 232.0, 228.5, 231.0, 9_000L), bars.get(2));
    }

    @Test
    void dailyBarsOfAnUnknownInstrumentAreEmpty() {
        when(marketData.findByInstrumentId(99)).thenReturn(Optional.empty());

        assertTrue(reader.dailyBars(99, TODAY).isEmpty());
        verifyNoInteractions(historyRepository);
    }

    @Test
    void intradayReadsCandlesFromTheExchangesMidnightAndEndsWithTheLivePrice() {
        // 2026-10-15 00:00 in New York (EDT, UTC-4) is 04:00 UTC.
        LocalDateTime dayStart = LocalDateTime.of(2026, 10, 15, 4, 0);
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(LIVE));
        when(candleRepository.findByInstrumentIdAndIntervalStartGreaterThanEqualOrderByIntervalStart(1, dayStart))
                .thenReturn(List.of(new PriceCandleEntity(1, LocalDateTime.of(2026, 10, 15, 13, 30),
                        new BigDecimal("229"), new BigDecimal("229.5"), new BigDecimal("228.9"),
                        new BigDecimal("229.2"), 100L)));

        List<PricePoint> points = reader.intraday(1);

        assertEquals(List.of(new PricePoint(Instant.parse("2026-10-15T13:31:00Z"), 229.2),
                new PricePoint(NOW, 231.0)), points);
    }

    @Test
    void withoutATradingDateOnlyTheStoredDaysAreReturned() {
        LocalDate from = LocalDate.of(2026, 10, 13);
        QuoteSnapshot undated = new QuoteSnapshot(AAPL, 231.0, 230.9, 231.1, 229.0, 232.0, 228.5, 228.0, 9_000L, NOW, null);
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(undated));
        when(historyRepository.findByInstrumentIdAndTradingDateGreaterThanEqualOrderByTradingDate(1, from))
                .thenReturn(List.of(bar(from, "225")));

        assertEquals(List.of(from), reader.dailyBars(1, from).stream().map(DailyBar::date).toList());
        // Nothing to file today's minutes under either.
        assertTrue(reader.intraday(1).isEmpty());
    }

    @Test
    void aRangeStartingAfterTodayHasNoLiveBar() {
        LocalDate tomorrow = TODAY.plusDays(1);
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(LIVE));
        when(historyRepository.findByInstrumentIdAndTradingDateGreaterThanEqualOrderByTradingDate(1, tomorrow))
                .thenReturn(List.of());

        assertTrue(reader.dailyBars(1, tomorrow).isEmpty());
    }

    @Test
    void intradayOfAnUnknownInstrumentIsEmptyAndAQuoteWithoutATimeAddsNoPoint() {
        when(marketData.findByInstrumentId(99)).thenReturn(Optional.empty());
        assertTrue(reader.intraday(99).isEmpty());

        QuoteSnapshot untimed = new QuoteSnapshot(AAPL, 231.0, 230.9, 231.1, 229.0, 232.0, 228.5, 228.0, 9_000L, null, TODAY);
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(untimed));
        when(candleRepository.findByInstrumentIdAndIntervalStartGreaterThanEqualOrderByIntervalStart(
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        assertTrue(reader.intraday(1).isEmpty());
    }

    private static PriceHistoryEntity bar(LocalDate date, String close) {
        BigDecimal price = new BigDecimal(close);
        return new PriceHistoryEntity(1, date, price, price, price, price, 1_000L);
    }
}
