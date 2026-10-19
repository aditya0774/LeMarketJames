package com.lemarketjames.market.model;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbmModelTest {

    @Test
    void zeroShockAndZeroVolatilityGrowsAtDrift() {
        // With no randomness, one year at 10% continuous drift multiplies price by e^0.10.
        double next = GbmModel.nextPrice(100.0, 0.10, 0.0, 1.0, 0.0);

        assertEquals(100.0 * Math.exp(0.10), next, 1e-9);
    }

    @Test
    void priceStaysPositiveUnderExtremeShocks() {
        assertTrue(GbmModel.nextPrice(100.0, 0.0, 2.0, 1.0, -50.0) > 0);
    }

    @Test
    void logReturnsMatchConfiguredMeanAndVariance() {
        // Statistical check with a fixed seed: log returns of the exact GBM step are normal with
        // mean (mu - sigma^2/2) * dt and variance sigma^2 * dt.
        double drift = 0.08;
        double volatility = 0.30;
        double dt = 1.0 / 252;
        int samples = 200_000;
        SplittableRandom random = new SplittableRandom(7);

        double sum = 0;
        double sumSquares = 0;
        for (int i = 0; i < samples; i++) {
            double logReturn = Math.log(GbmModel.nextPrice(1.0, drift, volatility, dt, random.nextGaussian()));
            sum += logReturn;
            sumSquares += logReturn * logReturn;
        }
        double mean = sum / samples;
        double variance = sumSquares / samples - mean * mean;

        double expectedMean = (drift - 0.5 * volatility * volatility) * dt;
        double expectedVariance = volatility * volatility * dt;
        // Standard error of the mean is sqrt(variance / n), roughly 4e-5 here.
        assertEquals(expectedMean, mean, 2e-4);
        assertEquals(expectedVariance, variance, expectedVariance * 0.02);
    }

    @Test
    void correlatedShockPreservesUnitVarianceAndTargetCorrelation() {
        double rho = 0.6;
        int samples = 200_000;
        SplittableRandom random = new SplittableRandom(11);

        double sumMarketTimesShock = 0;
        double sumShockSquares = 0;
        for (int i = 0; i < samples; i++) {
            double market = random.nextGaussian();
            double shock = GbmModel.correlatedShock(market, random.nextGaussian(), rho);
            sumMarketTimesShock += market * shock;
            sumShockSquares += shock * shock;
        }

        assertEquals(1.0, sumShockSquares / samples, 0.02);
        assertEquals(rho, sumMarketTimesShock / samples, 0.02);
    }
}
