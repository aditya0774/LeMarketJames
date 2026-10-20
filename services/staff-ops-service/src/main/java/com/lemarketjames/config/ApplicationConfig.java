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
     * Provides system Clock for time-based operations.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
