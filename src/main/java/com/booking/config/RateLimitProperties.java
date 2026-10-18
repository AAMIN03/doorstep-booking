package com.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Redis-backed API rate limiting ({@link com.booking.web.RateLimitFilter}).
 *
 * <p>Prefix {@code rate-limit.*} in {@code application.properties}. When {@link #enabled} is false,
 * or Redis cannot be reached at startup, limiting is skipped so the app still serves traffic.
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** When false, rate limiting is not applied. */
    private boolean enabled = true;

    /** Token-bucket capacity and greedy refill amount per {@link #windowMinutes}. */
    private int requestsPerMinute = 20;

    /** Refill window length in minutes (greedy refill). */
    private int windowMinutes = 1;

    private final Redis redis = new Redis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public Redis getRedis() {
        return redis;
    }

    /** Redis connection target for shared Bucket4j counters across app instances. */
    public static class Redis {

        private String host = "localhost";
        private int port = 6379;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
