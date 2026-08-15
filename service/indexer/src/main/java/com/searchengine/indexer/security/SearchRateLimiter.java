package com.searchengine.indexer.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.searchengine.indexer.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRateLimiter {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    private Cache<String, ClientWindow> rateLimitCache;

    @PostConstruct
    public void init() {
        if (searchProperties.getRateLimit().isEnabled()) {
            this.rateLimitCache = Caffeine.newBuilder()
                    .maximumSize(searchProperties.getRateLimit().getMaximumClientEntries())
                    .expireAfterWrite(searchProperties.getRateLimit().getWindow())
                    .build();
            log.info("SEARCH_RATE_LIMITER_INITIALIZED enabled=true maxClients={} window={}",
                    searchProperties.getRateLimit().getMaximumClientEntries(), searchProperties.getRateLimit().getWindow());
        }
    }

    public RateLimitResult checkRateLimit(String endpoint, String clientKey, int rpmLimit) {
        if (!searchProperties.getRateLimit().isEnabled() || rpmLimit <= 0) {
            return new RateLimitResult(true, rpmLimit, rpmLimit, Instant.now().getEpochSecond() + 60, 0);
        }

        long now = System.currentTimeMillis();
        long windowSizeMs = searchProperties.getRateLimit().getWindow().toMillis();
        String cacheKey = endpoint + ":" + clientKey;

        ClientWindow window = rateLimitCache.get(cacheKey, k -> new ClientWindow(now));

        synchronized (window) {
            if (now - window.startTimeMs >= windowSizeMs) {
                window.startTimeMs = now;
                window.count.set(0);
            }

            int currentCount = window.count.incrementAndGet();
            long resetTimestamp = (window.startTimeMs + windowSizeMs) / 1000;
            long retryAfter = Math.max(1, (window.startTimeMs + windowSizeMs - now) / 1000);

            if (currentCount <= rpmLimit) {
                int remaining = Math.max(0, rpmLimit - currentCount);
                meterRegistry.counter("search.rate_limit.allowed", "endpoint", endpoint).increment();
                return new RateLimitResult(true, rpmLimit, remaining, resetTimestamp, 0);
            } else {
                meterRegistry.counter("search.rate_limit.rejected", "endpoint", endpoint).increment();
                meterRegistry.counter("search.rate_limit." + endpoint + "_rejected").increment();
                return new RateLimitResult(false, rpmLimit, 0, resetTimestamp, retryAfter);
            }
        }
    }

    public void reset() {
        if (rateLimitCache != null) {
            rateLimitCache.invalidateAll();
        }
    }

    @Getter
    private static class ClientWindow {
        private long startTimeMs;
        private final AtomicInteger count = new AtomicInteger(0);

        public ClientWindow(long startTimeMs) {
            this.startTimeMs = startTimeMs;
        }
    }

    public record RateLimitResult(
            boolean allowed,
            int limit,
            int remaining,
            long resetUnixTimestamp,
            long retryAfterSeconds
    ) {}
}
