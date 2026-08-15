package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSearchRateLimiter {

    private final SearchProperties searchProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final SearchRateLimiter localRateLimiter;
    private final MeterRegistry meterRegistry;

    public SearchRateLimiter.RateLimitResult checkRateLimit(String endpoint, String clientKey, int rpmLimit) {
        SearchProperties.Redis redisProps = searchProperties.getRedis();

        if (!redisProps.isEnabled() || stringRedisTemplate == null) {
            return localRateLimiter.checkRateLimit(endpoint, clientKey, rpmLimit);
        }

        try {
            long windowMs = searchProperties.getRateLimit().getWindow().toMillis();
            long currentWindow = System.currentTimeMillis() / windowMs;
            String redisKey = redisProps.getKeyPrefix() + "rate-limit:" + endpoint + ":" + clientKey + ":" + currentWindow;

            Long currentCount = stringRedisTemplate.opsForValue().increment(redisKey);
            if (currentCount != null && currentCount == 1) {
                stringRedisTemplate.expire(redisKey, searchProperties.getRateLimit().getWindow().plus(Duration.ofSeconds(5)));
            }

            long now = System.currentTimeMillis();
            long windowStartMs = currentWindow * windowMs;
            long resetTimestamp = (windowStartMs + windowMs) / 1000;
            long retryAfter = Math.max(1, (windowStartMs + windowMs - now) / 1000);
            int count = currentCount != null ? currentCount.intValue() : 1;

            if (count <= rpmLimit) {
                int remaining = Math.max(0, rpmLimit - count);
                meterRegistry.counter("search.rate_limit.redis.allowed", "endpoint", endpoint).increment();
                log.debug("REDIS_RATE_LIMIT_ALLOWED endpoint={} remaining={}", endpoint, remaining);
                return new SearchRateLimiter.RateLimitResult(true, rpmLimit, remaining, resetTimestamp, 0);
            } else {
                meterRegistry.counter("search.rate_limit.redis.rejected", "endpoint", endpoint).increment();
                log.warn("REDIS_RATE_LIMIT_REJECTED endpoint={} count={} limit={}", endpoint, count, rpmLimit);
                return new SearchRateLimiter.RateLimitResult(false, rpmLimit, 0, resetTimestamp, retryAfter);
            }
        } catch (Exception e) {
            meterRegistry.counter("search.rate_limit.redis.errors").increment();
            meterRegistry.counter("search.rate_limit.redis.fallback").increment();
            log.warn("REDIS_CONNECTION_FAILED falling back to local rate limiter reason=\"{}\"", e.getMessage());

            if (redisProps.isFailOpen()) {
                log.info("REDIS_RATE_LIMIT_FALLBACK using local Caffeine rate limiter");
                return localRateLimiter.checkRateLimit(endpoint, clientKey, rpmLimit);
            } else {
                throw new RedisRateLimitUnavailableException("Rate limiting service temporarily unavailable", e);
            }
        }
    }

    public static class RedisRateLimitUnavailableException extends RuntimeException {
        public RedisRateLimitUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
