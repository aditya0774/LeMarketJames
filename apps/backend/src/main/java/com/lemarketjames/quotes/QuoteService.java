package com.lemarketjames.quotes;

import com.lemarketjames.quotes.dto.QuoteDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates real-time quotes for a fixed set of supported symbols.
 */
@Service
public class QuoteService {

    private final Map<String, QuoteSeed> seeds = new ConcurrentHashMap<>();

    public QuoteService() {
        seeds.put("AAPL", new QuoteSeed("AAPL", "Apple Inc.", bd("150.25"), bd("148.00"), 2_350_000_000_000L, bd("28.5"), bd("0.42"), 52_345_600L));
        seeds.put("MSFT", new QuoteSeed("MSFT", "Microsoft Corp.", bd("410.30"), bd("406.80"), 3_080_000_000_000L, bd("35.2"), bd("0.71"), 22_145_200L));
        seeds.put("GOOGL", new QuoteSeed("GOOGL", "Alphabet Inc.", bd("182.40"), bd("180.90"), 2_250_000_000_000L, bd("27.6"), bd("0.00"), 18_014_420L));
        seeds.put("AMZN", new QuoteSeed("AMZN", "Amazon.com Inc.", bd("197.15"), bd("194.50"), 2_050_000_000_000L, bd("54.9"), bd("0.00"), 31_104_100L));
        seeds.put("TSLA", new QuoteSeed("TSLA", "Tesla Inc.", bd("244.20"), bd("241.30"), 890_000_000_000L, bd("64.7"), bd("0.00"), 78_205_900L));
        seeds.put("NVDA", new QuoteSeed("NVDA", "NVIDIA Corp.", bd("131.80"), bd("129.10"), 3_220_000_000_000L, bd("71.4"), bd("0.03"), 95_408_770L));
    }

    public QuoteDto getQuote(String rawSymbol) {
        String symbol = normalize(rawSymbol);
        QuoteSeed seed = seeds.get(symbol);
        if (seed == null) {
            throw new SymbolNotFoundException("Symbol not found");
        }

        BigDecimal driftPercent = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(-0.015, 0.015));
        BigDecimal newPrice = seed.currentPrice
                .multiply(BigDecimal.ONE.add(driftPercent))
                .setScale(2, RoundingMode.HALF_UP);

        if (newPrice.compareTo(BigDecimal.ONE) < 0) {
            newPrice = BigDecimal.ONE;
        }

        BigDecimal priceChange = newPrice.subtract(seed.previousClose).setScale(2, RoundingMode.HALF_UP);
        BigDecimal priceChangePercent = priceChange
                .divide(seed.previousClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal highPrice = newPrice.max(seed.openPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lowPrice = newPrice.min(seed.openPrice).setScale(2, RoundingMode.HALF_UP);
        long nextVolume = Math.max(1L, seed.baseVolume + ThreadLocalRandom.current().nextLong(-600_000L, 600_001L));

        seed.currentPrice = newPrice;
        seed.baseVolume = nextVolume;

        return new QuoteDto(
                seed.symbol,
                seed.name,
                newPrice,
                priceChange,
                priceChangePercent,
                highPrice,
                lowPrice,
                seed.openPrice,
                nextVolume,
                seed.marketCap,
                seed.peRatio,
                seed.dividendYield,
                Instant.now());
    }

    private String normalize(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        return rawSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class QuoteSeed {
        private final String symbol;
        private final String name;
        private final BigDecimal previousClose;
        private final BigDecimal openPrice;
        private final long marketCap;
        private final BigDecimal peRatio;
        private final BigDecimal dividendYield;
        private BigDecimal currentPrice;
        private long baseVolume;

        private QuoteSeed(
                String symbol,
                String name,
                BigDecimal previousClose,
                BigDecimal openPrice,
                long marketCap,
                BigDecimal peRatio,
                BigDecimal dividendYield,
                long baseVolume) {
            this.symbol = symbol;
            this.name = name;
            this.previousClose = previousClose;
            this.openPrice = openPrice;
            this.marketCap = marketCap;
            this.peRatio = peRatio;
            this.dividendYield = dividendYield;
            this.currentPrice = previousClose;
            this.baseVolume = baseVolume;
        }
    }

    public static class SymbolNotFoundException extends RuntimeException {
        public SymbolNotFoundException(String message) {
            super(message);
        }
    }
}
