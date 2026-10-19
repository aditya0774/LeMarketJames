package com.lemarketjames.market.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the market simulation feature. Market timestamps use the service-wide UTC
 * {@code Clock} bean ({@link com.lemarketjames.config.ApplicationConfig}).
 *
 * <p>Scheduling is enabled separately in {@link MarketSchedulingConfig} so that it can be
 * switched off with {@code sim.enabled=false}.
 */
@Configuration
@EnableConfigurationProperties(MarketSimulationProperties.class)
public class MarketConfig {
}
