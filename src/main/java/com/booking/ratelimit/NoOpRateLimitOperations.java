package com.booking.ratelimit;

/**
 * Allows all requests when rate limiting is disabled or Redis is unavailable.
 */
public final class NoOpRateLimitOperations implements RateLimitOperations {

    @Override
    public boolean tryConsume(String clientKey) {
        return true;
    }
}
