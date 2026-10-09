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
     * The clock the activity window is measured with. A bean, so that a test can fix the time;
     * UTC, because fills are stored in UTC.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
