package com.lemarketjames.market.model;

/**
 * Immutable definition of a simulated instrument: its identity from {@code instruments} plus
 * its simulation settings from {@code instrument_market_params}.
 *
 * @param instrumentId      primary key shared with {@code instruments}
 * @param ticker            symbol, e.g. AAPL
 * @param name              display name
 * @param assetClass        EQUITY, FOREX or CRYPTO
 * @param currency          ISO currency code
 * @param location          market location (US, UK, INDIA); may be null
 * @param initialPrice      starting price when no stored snapshot exists
 * @param drift             annualised expected return (mu)
 * @param volatility        annualised volatility (sigma)
 * @param marketCorrelation 0..1, how strongly the price follows the overall market
 * @param spreadBps         bid/ask spread in basis points
 * @param sharesOutstanding shares for market cap; null when not applicable
 * @param avgDailyVolume    typical daily traded volume
 * @param earningsPerShare  annual EPS for the P/E ratio; null when not applicable
 * @param dividendPerShare  annual dividend per share
 */
public record MarketInstrument(
        int instrumentId,
        String ticker,
        String name,
        String assetClass,
        String currency,
        String location,
        double initialPrice,
        double drift,
        double volatility,
        double marketCorrelation,
        double spreadBps,
        Long sharesOutstanding,
        long avgDailyVolume,
        Double earningsPerShare,
        double dividendPerShare) {

    /** @return trading hours for this instrument's asset class and location */
    public MarketHours marketHours() {
        return MarketHours.forInstrument(assetClass, location);
    }
}
