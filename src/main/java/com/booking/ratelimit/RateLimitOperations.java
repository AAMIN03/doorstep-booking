package com.booking.ratelimit;

/**
 * Abstraction for consuming one token from a per-client rate limit bucket.
 * Implementations may use Redis (multi-instance) or no-op when limiting is disabled.
 */
public interface RateLimitOperations {

    /**
     * Attempts to consume a single request token for the given client key (typically client IP).
     *
     * @param clientKey stable key identifying the caller
     * @return {@code true} if the request is allowed; {@code false} if the limit is exceeded
     */
    boolean tryConsume(String clientKey);
}
