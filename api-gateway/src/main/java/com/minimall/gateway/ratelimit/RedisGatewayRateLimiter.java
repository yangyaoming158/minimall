package com.minimall.gateway.ratelimit;

import com.minimall.gateway.config.GatewayRateLimitProperties;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

public class RedisGatewayRateLimiter implements GatewayRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisGatewayRateLimiter.class);
    private static final RedisScript<List> RATE_LIMIT_SCRIPT = RedisScript.of("""
            local tokens_key = KEYS[1]
            local timestamp_key = KEYS[2]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local requested = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])

            local last_tokens = tonumber(redis.call("get", tokens_key))
            if last_tokens == nil then
                last_tokens = capacity
            end

            local last_refreshed = tonumber(redis.call("get", timestamp_key))
            if last_refreshed == nil then
                last_refreshed = now
            end

            local delta = math.max(0, now - last_refreshed)
            local filled_tokens = math.min(capacity, last_tokens + (delta * rate))
            local allowed = filled_tokens >= requested
            local new_tokens = filled_tokens

            if allowed then
                new_tokens = filled_tokens - requested
            end

            redis.call("setex", tokens_key, ttl, new_tokens)
            redis.call("setex", timestamp_key, ttl, now)

            if allowed then
                return {1, new_tokens}
            end
            return {0, new_tokens}
            """, List.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayRateLimitProperties properties;
    private final Clock clock;

    public RedisGatewayRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            GatewayRateLimitProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    RedisGatewayRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            GatewayRateLimitProperties properties,
            Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<GatewayRateLimitResult> isAllowed(String key) {
        List<String> redisKeys = redisKeys(key);
        List<String> args = List.of(
                String.valueOf(properties.getReplenishRate()),
                String.valueOf(properties.getBurstCapacity()),
                String.valueOf(properties.getRequestedTokens()),
                String.valueOf(clock.instant().getEpochSecond()),
                String.valueOf(properties.ttlSeconds()));

        return redisTemplate.execute(RATE_LIMIT_SCRIPT, redisKeys, args)
                .next()
                .map(this::toResult)
                .onErrorResume(exception -> {
                    log.warn("Gateway Redis rate limiter failed for key={}", key, exception);
                    if (properties.isFailOpen()) {
                        return Mono.just(GatewayRateLimitResult.allowed(-1));
                    }
                    return Mono.just(GatewayRateLimitResult.denied(0));
                });
    }

    private List<String> redisKeys(String key) {
        String sanitizedKey = key == null || key.isBlank() ? "unknown" : key.trim();
        String prefix = properties.getKeyPrefix() + ":" + sanitizedKey;
        return List.of(prefix + ":tokens", prefix + ":timestamp");
    }

    private GatewayRateLimitResult toResult(List values) {
        long allowed = numberAt(values, 0);
        long remaining = numberAt(values, 1);
        if (allowed == 1L) {
            return GatewayRateLimitResult.allowed(remaining);
        }
        return GatewayRateLimitResult.denied(remaining);
    }

    private long numberAt(List values, int index) {
        Object value = values.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
