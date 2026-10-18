package com.booking.config;

import com.booking.ratelimit.NoOpRateLimitOperations;
import com.booking.ratelimit.RateLimitOperations;
import com.booking.ratelimit.RedisRateLimitOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link RateLimitOperations}: Redis + Bucket4j when enabled and reachable, otherwise no-op.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * Creates shared rate-limit operations. Never fails application startup; falls back to no-op.
     */
    @Bean
    RateLimitOperations rateLimitOperations(RateLimitProperties properties) {
        if (!properties.isEnabled()) {
            log.info("Rate limiting disabled (rate-limit.enabled=false)");
            return new NoOpRateLimitOperations();
        }
        try {
            return RedisRateLimitOperations.create(properties);
        } catch (Exception ex) {
            log.warn("Rate limiting disabled: could not connect to Redis at {}:{}", properties.getRedis().getHost(),
                    properties.getRedis().getPort(), ex);
            return new NoOpRateLimitOperations();
        }
    }
}
