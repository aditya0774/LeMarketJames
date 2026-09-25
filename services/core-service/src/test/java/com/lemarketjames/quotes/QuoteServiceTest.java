package com.lemarketjames.quotes;

import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.quotes.dto.QuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// market moved out to market-service; MarketDataService is mocked here (the real simulator's
// tick/invariant behavior is covered by MarketSimulatorTest in market-service) since QuoteService's
// own job is only translating a QuoteSnapshot into a QuoteDto.
class QuoteServiceTest {

    @Mock
    private MarketDataService marketData;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        quoteService = new QuoteService(marketData);
        when(marketData.findByTicker(anyString())).thenReturn(Optional.empty());
        when(marketData.findByTicker("AAPL")).thenReturn(Optional.of(quoteFor(instrument(1, "AAPL", "Apple Inc", 227.55))));
        when(marketData.findByTicker("MSFT")).thenReturn(Optional.of(quoteFor(instrument(2, "MSFT", "Microsoft Corp", 429.85))));
    }

    @Test
    void returnsQuoteForKnownSymbol() {
        QuoteDto quote = quoteService.getQuote("AAPL");

        assertEquals("AAPL", quote.getSymbol());
        assertNotNull(quote.getName());
        assertNotNull(quote.getPrice());
        assertNotNull(quote.getLastUpdate());
        assertTrue(quote.getPrice().doubleValue() > 0);
    }

    @Test
    void normalizesSymbolToUpperCase() {
        QuoteDto quote = quoteService.getQuote("msft");

        assertEquals("MSFT", quote.getSymbol());
    }

    @Test
    void rejectsUnknownSymbol() {
        assertThrows(QuoteService.SymbolNotFoundException.class, () -> quoteService.getQuote("UNKNOWN"));
    }

    @Test
    void rejectsBlankSymbol() {
        assertThrows(IllegalArgumentException.class, () -> quoteService.getQuote("   "));
    }

    @Test
    void readingAQuoteTwiceReturnsTheSamePrice() {
        // Regression: the previous implementation changed the price on every request.
        BigDecimal first = quoteService.getQuote("AAPL").getPrice();
        for (int i = 0; i < 20; i++) {
            assertEquals(first, quoteService.getQuote("AAPL").getPrice());
        }
    }

    @Test
    void derivesFundamentalsFromPrice() {
        QuoteDto quote = quoteService.getQuote("AAPL");

        // 227.55 * 15.2bn shares, EPS 6.75 and a 1.00 dividend, as configured below.
        assertEquals(Math.round(227.55 * 15_200_000_000L), quote.getMarketCap());
        assertEquals(new BigDecimal("33.71"), quote.getPeRatio());
        assertEquals(new BigDecimal("0.44"), quote.getDividendYield());
        assertEquals(new BigDecimal("0.00"), quote.getPriceChange());
    }

    @Test
    void returnsEveryQuoteSortedBySymbol() {
        when(marketData.findAll()).thenReturn(List.of(
                quoteFor(instrument(2, "MSFT", "Bronisoft", 429.85)),
                quoteFor(instrument(1, "AAPL", "BronApple", 227.55))));

        List<QuoteDto> quotes = quoteService.getAllQuotes();

        assertEquals(List.of("AAPL", "MSFT"), quotes.stream().map(QuoteDto::getSymbol).toList());
        // Same mapping as the single-symbol lookup.
        assertEquals(new BigDecimal("227.55"), quotes.get(0).getPrice());
        assertEquals("BronApple", quotes.get(0).getName());
    }

    @Test
    void returnsNoQuotesWhenTheMarketIsUnavailable() {
        when(marketData.findAll()).thenReturn(List.of());

        assertTrue(quoteService.getAllQuotes().isEmpty());
    }

    private static MarketInstrument instrument(int id, String ticker, String name, double price) {
        return new MarketInstrument(id, ticker, name, "EQUITY", "USD", "US",
                price, 0.08, 0.25, 0.6, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    }

    /** A quote priced exactly at the instrument's initial price and unchanged from the previous close. */
    private static QuoteSnapshot quoteFor(MarketInstrument instrument) {
        return new QuoteSnapshot(instrument, instrument.initialPrice(), instrument.initialPrice() - 0.05,
                instrument.initialPrice() + 0.05, instrument.initialPrice(), instrument.initialPrice(),
                instrument.initialPrice(), instrument.initialPrice(), 1_000_000L, Instant.now(), LocalDate.now());
    }
}
