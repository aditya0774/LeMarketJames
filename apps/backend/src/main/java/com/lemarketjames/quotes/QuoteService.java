package com.lemarketjames.quotes;

import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.quotes.dto.QuoteDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Serves quotes from the simulated market.
 *
 * <p>Read-only: requesting a quote never changes a price. Prices are advanced on a timer by
 * {@link com.lemarketjames.market.service.MarketSimulator}, so every caller sees the same
 * market regardless of how often quotes are requested.
 */
@Service
public class QuoteService {

    /** Contract examples show prices and percentages with 2 decimal places. */
    private static final int DISPLAY_SCALE = 2;

    private final MarketDataService marketData;

    public QuoteService(MarketDataService marketData) {
        this.marketData = marketData;
    }

    public QuoteDto getQuote(String rawSymbol) {
        String symbol = normalize(rawSymbol);
        QuoteSnapshot snapshot = marketData.findByTicker(symbol)
                .orElseThrow(() -> new SymbolNotFoundException("Symbol not found"));

        return new QuoteDto(
                snapshot.instrument().ticker(),
                snapshot.instrument().name(),
                money(snapshot.lastPrice()),
                money(snapshot.priceChange()),
                money(snapshot.priceChangePercent()),
                money(snapshot.highPrice()),
                money(snapshot.lowPrice()),
                money(snapshot.openPrice()),
                snapshot.volume(),
                snapshot.marketCap(),
                money(snapshot.peRatio()),
                money(snapshot.dividendYieldPercent()),
                snapshot.lastUpdated());
    }

    private String normalize(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        return rawSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    public static class SymbolNotFoundException extends RuntimeException {
        public SymbolNotFoundException(String message) {
            super(message);
        }
    }
}
