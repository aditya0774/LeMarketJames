package com.lemarketjames.market.model;

/**
 * Geometric Brownian Motion (GBM) price math.
 *
 * <p>GBM models a price whose log returns are normally distributed:
 * {@code dS = mu * S * dt + sigma * S * dW}. We use the exact discrete solution rather than a
 * linear (Euler) approximation, which guarantees prices stay positive and avoids a
 * systematic upward bias:
 *
 * <pre>
 *   S(t + dt) = S(t) * exp((mu - sigma^2 / 2) * dt + sigma * sqrt(dt) * Z),   Z ~ N(0, 1)
 * </pre>
 *
 * <p>Kept free of Spring and state so the maths can be unit tested in isolation.
 */
public final class GbmModel {

    private GbmModel() {
    }

    /**
     * Advances a price by one time step.
     *
     * @param price      current price, must be positive
     * @param drift      annualised expected return (mu), e.g. 0.08
     * @param volatility annualised volatility (sigma), e.g. 0.25
     * @param dtYears    length of the step in trading years
     * @param shock      standard normal random draw (Z)
     * @return the next price, always positive
     */
    public static double nextPrice(double price, double drift, double volatility, double dtYears, double shock) {
        // The -sigma^2/2 term (Ito correction) keeps the *expected* price growing at mu;
        // without it simulated prices would drift upward faster than configured.
        double logReturn = (drift - 0.5 * volatility * volatility) * dtYears
                + volatility * Math.sqrt(dtYears) * shock;
        return price * Math.exp(logReturn);
    }

    /**
     * Blends a market-wide shock with an instrument-specific shock using a one-factor model:
     * {@code Z = rho * Z_market + sqrt(1 - rho^2) * Z_own}.
     *
     * <p>The result is still standard normal, so volatility is unchanged, but instruments with
     * a higher {@code rho} move together more often, as real stocks do on market-wide news.
     *
     * @param marketShock       shared standard normal draw for this tick
     * @param idiosyncraticShock instrument's own standard normal draw
     * @param marketCorrelation rho in [0, 1]
     * @return the correlated standard normal shock
     */
    public static double correlatedShock(double marketShock, double idiosyncraticShock, double marketCorrelation) {
        return marketCorrelation * marketShock
                + Math.sqrt(1.0 - marketCorrelation * marketCorrelation) * idiosyncraticShock;
    }
}
