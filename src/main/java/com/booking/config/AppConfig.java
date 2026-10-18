package com.booking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injectable clock for deterministic scheduling tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
