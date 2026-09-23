package com.lemarketjames.quotes;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.service.MarketSimulator;
import com.lemarketjames.quotes.dto.QuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteServiceTest {

    /** A Wednesday at 15:00 UTC = 11:00 New York, inside US market hours. */
    private static final Instant MARKET_OPEN = Instant.parse("2026-09-16T15:00:00Z");

    private MarketSimulator simulator;
    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        MarketSimulationProperties properties = new MarketSimulationProperties();
        properties.setSeed(42L);
        simulator = new MarketSimulator(properties, Clock.fixed(MARKET_OPEN, ZoneOffset.UTC));
        simulator.load(List.of(
                instrument(1, "AAPL", "Apple Inc", 227.55, 0.25),
                instrument(2, "MSFT", "Microsoft Corp", 429.85, 0.23),
                instrument(5, "TSLA", "Tesla Inc", 244.20, 0.55)), Map.of());
        quoteService = new QuoteService(simulator);
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
    void readingAQuoteDoesNotMoveThePrice() {
        // Regression: the previous implementation changed the price on every request.
        BigDecimal first = quoteService.getQuote("AAPL").getPrice();
        for (int i = 0; i < 20; i++) {
            assertEquals(first, quoteService.getQuote("AAPL").getPrice());
        }
    }

    @Test
    void keepsQuoteInvariantsAcrossTicks() {
        for (int i = 1; i <= 75; i++) {
            simulator.tick(MARKET_OPEN.plusSeconds(i));
            QuoteDto quote = quoteService.getQuote("TSLA");
            assertTrue(quote.getPrice().doubleValue() > 0);
            assertTrue(quote.getHighPrice().doubleValue() >= quote.getLowPrice().doubleValue());
            assertTrue(quote.getPrice().compareTo(quote.getHighPrice()) <= 0);
            assertTrue(quote.getPrice().compareTo(quote.getLowPrice()) >= 0);
            assertTrue(quote.getVolume() > 0);
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

    private static MarketInstrument instrument(int id, String ticker, String name, double price, double volatility) {
        return new MarketInstrument(id, ticker, name, "EQUITY", "USD", "US",
                price, 0.08, volatility, 0.6, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
    }
}
