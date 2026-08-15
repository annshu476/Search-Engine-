package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSearchRateLimiterTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchRateLimiter localRateLimiter;
    private RedisSearchRateLimiter redisRateLimiter;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getRedis().setEnabled(true);
        searchProperties.getRedis().setFailOpen(true);

        meterRegistry = new SimpleMeterRegistry();
        localRateLimiter = new SearchRateLimiter(searchProperties, meterRegistry);
        localRateLimiter.init();

        // Pass null StringRedisTemplate to test fallback
        redisRateLimiter = new RedisSearchRateLimiter(searchProperties, null, localRateLimiter, meterRegistry);
    }

    @Test
    void checkRateLimit_nullRedisTemplateFailOpen_fallsBackToLocalRateLimiter() {
        SearchRateLimiter.RateLimitResult result = redisRateLimiter.checkRateLimit("search", "client1", 60);

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(60);
        assertThat(result.remaining()).isEqualTo(59);
    }

    @Test
    void checkRateLimit_redisDisabled_fallsBackToLocalRateLimiter() {
        searchProperties.getRedis().setEnabled(false);

        SearchRateLimiter.RateLimitResult result = redisRateLimiter.checkRateLimit("search", "client1", 60);

        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
    }
}
