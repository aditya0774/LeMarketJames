package com.lemarketjames.market.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring for the market simulation feature.
 *
 * <p>Scheduling is enabled separately in {@link MarketSchedulingConfig} so that it can be
 * switched off with {@code sim.enabled=false}.
 */
@Configuration
@EnableConfigurationProperties(MarketSimulationProperties.class)
public class MarketConfig {

    /**
     * UTC clock used for all market timestamps. Exposed as a bean so tests can substitute a
     * fixed or manually advanced clock.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
