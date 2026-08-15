package com.searchengine.indexer.security;

import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRateLimiterTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getRateLimit().setEnabled(true);
        searchProperties.getRateLimit().setMaximumClientEntries(1000);
        searchProperties.getRateLimit().setWindow(Duration.ofMinutes(1));

        meterRegistry = new SimpleMeterRegistry();
        rateLimiter = new SearchRateLimiter(searchProperties, meterRegistry);
        rateLimiter.init();
    }

    @Test
    void checkRateLimit_firstRequest_isAllowed() {
        SearchRateLimiter.RateLimitResult result = rateLimiter.checkRateLimit("search", "client1", 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(60);
        assertThat(result.remaining()).isEqualTo(59);
    }

    @Test
    void checkRateLimit_exceedingLimit_isRejected() {
        int limit = 5;
        for (int i = 0; i < limit; i++) {
            SearchRateLimiter.RateLimitResult res = rateLimiter.checkRateLimit("search", "client1", limit);
            assertThat(res.allowed()).isTrue();
        }

        SearchRateLimiter.RateLimitResult rejectedRes = rateLimiter.checkRateLimit("search", "client1", limit);
        assertThat(rejectedRes.allowed()).isFalse();
        assertThat(rejectedRes.remaining()).isEqualTo(0);
        assertThat(rejectedRes.retryAfterSeconds()).isGreaterThan(0L);
    }

    @Test
    void checkRateLimit_differentClients_haveIndependentLimits() {
        int limit = 2;
        rateLimiter.checkRateLimit("search", "client1", limit);
        rateLimiter.checkRateLimit("search", "client1", limit);

        SearchRateLimiter.RateLimitResult client1Res = rateLimiter.checkRateLimit("search", "client1", limit);
        assertThat(client1Res.allowed()).isFalse();

        SearchRateLimiter.RateLimitResult client2Res = rateLimiter.checkRateLimit("search", "client2", limit);
        assertThat(client2Res.allowed()).isTrue();
    }

    @Test
    void checkRateLimit_disabledLimiter_alwaysAllows() {
        searchProperties.getRateLimit().setEnabled(false);

        for (int i = 0; i < 100; i++) {
            SearchRateLimiter.RateLimitResult res = rateLimiter.checkRateLimit("search", "client1", 10);
            assertThat(res.allowed()).isTrue();
        }
    }

    @Test
    void checkRateLimit_concurrentRequests_handlesThreadSafety() throws Exception {
        int threads = 10;
        int perThread = 10;
        int limit = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        rateLimiter.checkRateLimit("search", "client_concurrent", limit);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        SearchRateLimiter.RateLimitResult res = rateLimiter.checkRateLimit("search", "client_concurrent", limit);
        assertThat(res.allowed()).isFalse();
    }
}
