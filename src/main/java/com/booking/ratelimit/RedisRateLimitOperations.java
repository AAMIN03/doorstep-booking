package com.booking.ratelimit;

import com.booking.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bucket4j token bucket stored in Redis via Lettuce, shared across application instances.
 */
public final class RedisRateLimitOperations implements RateLimitOperations {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitOperations.class);
    private static final String KEY_PREFIX = "doorstep:rate:";

    private final ProxyManager<byte[]> proxyManager;
    private final BucketConfiguration bucketConfiguration;

    private RedisRateLimitOperations(ProxyManager<byte[]> proxyManager, BucketConfiguration bucketConfiguration) {
        this.proxyManager = proxyManager;
        this.bucketConfiguration = bucketConfiguration;
    }

    /**
     * Connects to Redis and builds a proxy manager. Throws if Redis is not reachable.
     *
     * @param properties
     *            limit and Redis connection settings
     *
     * @return operations backed by Redis
     */
    public static RedisRateLimitOperations create(RateLimitProperties properties) {
        String redisUri = "redis://" + properties.getRedis().getHost() + ":" + properties.getRedis().getPort();
        RedisClient client = RedisClient.create(redisUri);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            String pong = connection.sync().ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis PING failed: " + pong);
            }
        }
        ProxyManager<byte[]> proxyManager = LettuceBasedProxyManager.builderFor(client).build();
        int capacity = properties.getRequestsPerMinute();
        Duration window = Duration.ofMinutes(properties.getWindowMinutes());
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build()).build();
        log.info("Redis rate limiting active: {} requests per {} minute(s) at {}", capacity,
                properties.getWindowMinutes(), redisUri);
        return new RedisRateLimitOperations(proxyManager, configuration);
    }

    @Override
    public boolean tryConsume(String clientKey) {
        byte[] key = (KEY_PREFIX + clientKey).getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(key, () -> bucketConfiguration).tryConsume(1);
    }
}
