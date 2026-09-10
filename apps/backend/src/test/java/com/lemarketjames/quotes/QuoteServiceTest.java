package com.lemarketjames.quotes;

import com.lemarketjames.quotes.dto.QuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteServiceTest {

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService();
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
    void keepsQuoteInvariantsAcrossTicks() {
        for (int i = 0; i < 75; i++) {
            QuoteDto quote = quoteService.getQuote("TSLA");
            assertTrue(quote.getPrice().doubleValue() > 0);
            assertTrue(quote.getHighPrice().doubleValue() >= quote.getLowPrice().doubleValue());
            assertTrue(quote.getVolume() > 0);
        }
    }
}
