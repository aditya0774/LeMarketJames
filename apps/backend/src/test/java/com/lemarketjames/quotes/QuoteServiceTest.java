package com.lemarketjames.quotes;

import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.quotes.dto.QuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Quote Service Unit Tests")
class QuoteServiceTest {

    @Mock
    private MarketDataService marketDataService;

    private QuoteService quoteService;
    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(marketDataService);
    }

    // ========== Success Path Tests ==========

    @Test
    @DisplayName("getQuote returns valid QuoteDto for known symbol")
    void testGetQuoteReturnsValidDto() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.55, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        assertNotNull(result);
        assertEquals("AAPL", result.getSymbol());
        assertEquals("Apple Inc", result.getName());
        assertNotNull(result.getPrice());
        assertNotNull(result.getLastUpdate());
    }

    @Test
    @DisplayName("getQuote normalizes symbol to uppercase")
    void testGetQuoteNormalizesSymbolToUppercase() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.55, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("aapl");

        assertEquals("AAPL", result.getSymbol());
    }

    @Test
    @DisplayName("getQuote trims whitespace from symbol")
    void testGetQuoteTrimsWhitespace() {
        MarketInstrument instrument = new MarketInstrument(1, "MSFT", "Microsoft Corp", "EQUITY", "USD", "US",
                429.85, 0.09, 0.23, 0.65, 2.0, 7_430_000_000L, 22_000_000L, 12.10, 3.32);
        QuoteSnapshot snapshot = createSnapshot(instrument, 429.85, 432.0, 428.0, 430.0, 428.0, 2_000_000L);

        when(marketDataService.findByTicker("MSFT")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("  msft  ");

        assertEquals("MSFT", result.getSymbol());
    }

    @Test
    @DisplayName("getQuote rounds prices to 2 decimal places")
    void testGetQuoteRoundsToTwoDecimals() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.554, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.554, 230.001, 225.999, 228.125, 226.456, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        assertEquals(new BigDecimal("227.55"), result.getPrice());
        assertEquals(new BigDecimal("230.00"), result.getHighPrice());
        assertEquals(new BigDecimal("226.00"), result.getLowPrice());
    }

    @Test
    @DisplayName("getQuote uses HALF_UP rounding mode")
    void testGetQuoteUsesHalfUpRounding() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.555, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.555, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        // 227.555 rounded with HALF_UP to 2 decimals should be 227.56
        assertEquals(new BigDecimal("227.56"), result.getPrice());
    }

    @Test
    @DisplayName("getQuote includes all quote fields")
    void testGetQuoteIncludesAllFields() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.55, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        assertNotNull(result.getSymbol());
        assertNotNull(result.getName());
        assertNotNull(result.getPrice());
        assertNotNull(result.getPriceChange());
        assertNotNull(result.getPriceChangePercent());
        assertNotNull(result.getHighPrice());
        assertNotNull(result.getLowPrice());
        assertNotNull(result.getOpenPrice());
        assertNotNull(result.getVolume());
        assertNotNull(result.getMarketCap());
        assertNotNull(result.getPeRatio());
        assertNotNull(result.getDividendYield());
        assertNotNull(result.getLastUpdate());
    }

    @Test
    @DisplayName("getQuote calculates market cap from price and shares")
    void testGetQuoteCalculatesMarketCap() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.55, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        long expectedMarketCap = Math.round(227.55 * 15_200_000_000L);
        assertEquals(expectedMarketCap, result.getMarketCap());
    }

    @Test
    @DisplayName("getQuote calculates P/E ratio from price and EPS")
    void testGetQuoteCalculatesPeRatio() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.55, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        // P/E = 227.55 / 6.75 = 33.71
        assertEquals(new BigDecimal("33.71"), result.getPeRatio());
    }

    @Test
    @DisplayName("getQuote calculates dividend yield from price and dividend")
    void testGetQuoteCalculatesDividendYield() {
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot snapshot = createSnapshot(instrument, 227.55, 230.0, 225.0, 228.0, 226.0, 1_000_000L);

        when(marketDataService.findByTicker("AAPL")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("AAPL");

        // Dividend yield = (1.00 / 227.55) * 100 = 0.44%
        assertEquals(new BigDecimal("0.44"), result.getDividendYield());
    }

    // ========== Null and Blank Symbol Tests ==========

    @Test
    @DisplayName("getQuote throws IllegalArgumentException for null symbol")
    void testGetQuoteThrowsForNullSymbol() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> quoteService.getQuote(null));
        assertEquals("Symbol is required", ex.getMessage());
    }

    @Test
    @DisplayName("getQuote throws IllegalArgumentException for blank symbol")
    void testGetQuoteThrowsForBlankSymbol() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> quoteService.getQuote("   "));
        assertEquals("Symbol is required", ex.getMessage());
    }

    @Test
    @DisplayName("getQuote throws IllegalArgumentException for empty string")
    void testGetQuoteThrowsForEmptyString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> quoteService.getQuote(""));
        assertEquals("Symbol is required", ex.getMessage());
    }

    // ========== Symbol Not Found Tests ==========

    @Test
    @DisplayName("getQuote throws SymbolNotFoundException for unknown symbol")
    void testGetQuoteThrowsForUnknownSymbol() {
        when(marketDataService.findByTicker("UNKNOWN")).thenReturn(Optional.empty());

        QuoteService.SymbolNotFoundException ex = assertThrows(
                QuoteService.SymbolNotFoundException.class,
                () -> quoteService.getQuote("UNKNOWN"));
        assertEquals("Symbol not found", ex.getMessage());
    }

    @Test
    @DisplayName("SymbolNotFoundException is RuntimeException")
    void testSymbolNotFoundExceptionIsRuntime() {
        when(marketDataService.findByTicker("XYZ")).thenReturn(Optional.empty());

        assertTrue(RuntimeException.class.isInstance(
                assertThrows(QuoteService.SymbolNotFoundException.class, () -> quoteService.getQuote("XYZ"))));
    }

    // ========== Edge Case Tests ==========

    @Test
    @DisplayName("getQuote handles zero prices")
    void testGetQuoteHandlesZeroPrices() {
        MarketInstrument instrument = new MarketInstrument(1, "ZERO", "Zero Corp", "EQUITY", "USD", "US",
                0.0, 0.08, 0.25, 0.65, 2.0, 1_000_000_000L, 1_000_000L, 0.01, 0.0);
        QuoteSnapshot snapshot = createSnapshot(instrument, 0.0, 0.1, 0.0, 0.05, 0.0, 1_000L);

        when(marketDataService.findByTicker("ZERO")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("ZERO");

        assertEquals(new BigDecimal("0.00"), result.getPrice());
    }

    @Test
    @DisplayName("getQuote handles large prices")
    void testGetQuoteHandlesLargePrices() {
        MarketInstrument instrument = new MarketInstrument(1, "LARGE", "Large Corp", "EQUITY", "USD", "US",
                99999.99, 0.08, 0.25, 0.65, 2.0, 1_000_000_000L, 1_000_000L, 10000.0, 100.0);
        QuoteSnapshot snapshot = createSnapshot(instrument, 99999.99, 100000.0, 99000.0, 99500.0, 99000.0, 1_000_000L);

        when(marketDataService.findByTicker("LARGE")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("LARGE");

        assertNotNull(result);
        assertTrue(result.getPrice().doubleValue() > 0);
    }

    @Test
    @DisplayName("getQuote handles various symbol formats")
    void testGetQuoteHandsVariousSymbolFormats() {
        MarketInstrument instrument = new MarketInstrument(1, "BRK.B", "Berkshire Hathaway B", "EQUITY", "USD", "US",
                400.0, 0.08, 0.25, 0.65, 2.0, 1_000_000_000L, 1_000_000L, 50.0, 0.0);
        QuoteSnapshot snapshot = createSnapshot(instrument, 400.0, 410.0, 390.0, 405.0, 395.0, 1_000_000L);

        when(marketDataService.findByTicker("BRK.B")).thenReturn(Optional.of(snapshot));

        QuoteDto result = quoteService.getQuote("brk.b");

        assertEquals("BRK.B", result.getSymbol());
    }

    // ========== Helper Methods ==========

    private QuoteSnapshot createSnapshot(MarketInstrument instrument, double lastPrice, double highPrice,
                                        double lowPrice, double openPrice, double previousClose, long volume) {
        return new QuoteSnapshot(
                instrument,
                lastPrice,
                lastPrice - 0.01,  // bidPrice
                lastPrice + 0.01,  // askPrice
                openPrice,
                highPrice,
                lowPrice,
                previousClose,
                volume,
                NOW,
                NOW.atZone(ZoneOffset.UTC).toLocalDate()
        );
    }
}
