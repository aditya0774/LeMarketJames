package com.lemarketjames.market.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler only when the simulation is enabled, so test contexts
 * ({@code sim.enabled=false}) have no background threads changing prices mid-test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sim", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketSchedulingConfig {
}
