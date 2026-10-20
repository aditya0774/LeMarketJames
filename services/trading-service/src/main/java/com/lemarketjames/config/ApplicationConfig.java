package com.lemarketjames.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-wide configuration beans.
 */
@Configuration
public class ApplicationConfig {

    /**
     * The one clock of this service: market timestamps and the activity window are measured with
     * it. A bean, so that a test can fix or advance the time; UTC, because prices and fills are
     * stored in UTC.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
