package com.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-10-15T02:30:00Z");

    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
    }
}
