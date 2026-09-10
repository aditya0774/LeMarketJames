package com.lemarketjames.quotes.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contract-aligned quote payload for GET /api/quotes/{symbol}.
 */
public class QuoteDto {

    private final String symbol;
    private final String name;
    private final BigDecimal price;
    private final BigDecimal priceChange;
    private final BigDecimal priceChangePercent;
    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final BigDecimal openPrice;
    private final long volume;
    private final long marketCap;
    private final BigDecimal peRatio;
    private final BigDecimal dividendYield;
    private final Instant lastUpdate;

    public QuoteDto(
            String symbol,
            String name,
            BigDecimal price,
            BigDecimal priceChange,
            BigDecimal priceChangePercent,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal openPrice,
            long volume,
            long marketCap,
            BigDecimal peRatio,
            BigDecimal dividendYield,
            Instant lastUpdate) {
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.priceChange = priceChange;
        this.priceChangePercent = priceChangePercent;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.openPrice = openPrice;
        this.volume = volume;
        this.marketCap = marketCap;
        this.peRatio = peRatio;
        this.dividendYield = dividendYield;
        this.lastUpdate = lastUpdate;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getPriceChange() {
        return priceChange;
    }

    public BigDecimal getPriceChangePercent() {
        return priceChangePercent;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public long getVolume() {
        return volume;
    }

    public long getMarketCap() {
        return marketCap;
    }

    public BigDecimal getPeRatio() {
        return peRatio;
    }

    public BigDecimal getDividendYield() {
        return dividendYield;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }
}
