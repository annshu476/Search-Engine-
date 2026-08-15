package com.searchengine.indexer.integration;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.security.RedisSearchRateLimiter;
import com.searchengine.indexer.security.SearchRateLimiter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
class RedisRateLimitIntegrationTest {

    @Autowired
    private RedisSearchRateLimiter redisSearchRateLimiter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SearchProperties searchProperties;

    @Test
    void distributedRateLimiting_acrossSimulatedInstances_enforcesSharedLimit() {
        if (stringRedisTemplate == null) {
            return; // Skip if Redis connection not active
        }

        String testClientHash = "simulated_client_" + UUID.randomUUID();
        int sharedLimit = 5;

        // Instance A makes 3 requests
        for (int i = 0; i < 3; i++) {
            SearchRateLimiter.RateLimitResult resA = redisSearchRateLimiter.checkRateLimit("search", testClientHash, sharedLimit);
            assertThat(resA.allowed()).isTrue();
        }

        // Instance B makes 2 requests
        for (int i = 0; i < 2; i++) {
            SearchRateLimiter.RateLimitResult resB = redisSearchRateLimiter.checkRateLimit("search", testClientHash, sharedLimit);
            assertThat(resB.allowed()).isTrue();
        }

        // 6th request from Instance A should be REJECTED!
        SearchRateLimiter.RateLimitResult res6 = redisSearchRateLimiter.checkRateLimit("search", testClientHash, sharedLimit);
        assertThat(res6.allowed()).isFalse();
        assertThat(res6.remaining()).isEqualTo(0);
        assertThat(res6.retryAfterSeconds()).isGreaterThan(0L);
    }
}
