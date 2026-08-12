package com.searchengine.crawler.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-origin request rate limiter enforcing minimum request intervals and max active concurrency.
 */
@Component
public class DomainRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainRateLimiter.class);

    private final Duration defaultDelay;
    private final Duration maxDelay;
    private final int maxConcurrentPerOrigin;
    private final Cache<String, OriginRateState> stateCache;
    private final Supplier<Long> clockSupplier;
    private final Sleeper sleeper;

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @Autowired
    public DomainRateLimiter(
            @Value("${crawler.rate-limit.default-delay:2s}") Duration defaultDelay,
            @Value("${crawler.rate-limit.max-delay:60s}") Duration maxDelay,
            @Value("${crawler.rate-limit.max-concurrent-per-origin:1}") int maxConcurrentPerOrigin,
            @Value("${crawler.rate-limit.cache-max-size:1000}") long cacheMaxSize,
            @Value("${crawler.rate-limit.state-expiration:30m}") Duration stateExpiration
    ) {
        this(defaultDelay, maxDelay, maxConcurrentPerOrigin, cacheMaxSize, stateExpiration, System::currentTimeMillis, Thread::sleep);
    }

    public DomainRateLimiter(
            Duration defaultDelay,
            Duration maxDelay,
            int maxConcurrentPerOrigin,
            long cacheMaxSize,
            Duration stateExpiration,
            Supplier<Long> clockSupplier,
            Sleeper sleeper
    ) {
        this.defaultDelay = defaultDelay;
        this.maxDelay = maxDelay;
        this.maxConcurrentPerOrigin = maxConcurrentPerOrigin;
        this.clockSupplier = clockSupplier;
        this.sleeper = sleeper;
        this.stateCache = Caffeine.newBuilder()
                .expireAfterAccess(stateExpiration)
                .maximumSize(cacheMaxSize)
                .build();
    }

    public static class OriginRateState {
        private final Semaphore semaphore;
        private long lastStartTimestampMs;

        public OriginRateState(int maxConcurrent) {
            this.semaphore = new Semaphore(maxConcurrent, true);
            this.lastStartTimestampMs = 0L;
        }

        public Semaphore getSemaphore() {
            return semaphore;
        }

        public synchronized long getLastStartTimestampMs() {
            return lastStartTimestampMs;
        }

        public synchronized void setLastStartTimestampMs(long timestampMs) {
            this.lastStartTimestampMs = timestampMs;
        }
    }

    public void acquire(String originOrUrl, long robotsCrawlDelayMs) {
        String origin = canonicalizeOrigin(originOrUrl);
        long effectiveDelayMs = calculateEffectiveDelay(robotsCrawlDelayMs);

        OriginRateState state = stateCache.get(origin, key -> new OriginRateState(maxConcurrentPerOrigin));

        try {
            state.getSemaphore().acquire();

            long now = clockSupplier.get();
            long lastStart = state.getLastStartTimestampMs();
            long timeSinceLastStart = now - lastStart;
            long waitMs = effectiveDelayMs - timeSinceLastStart;

            if (waitMs > 0) {
                LOGGER.info("RATE_LIMIT_WAIT origin={} waitMs={}", origin, waitMs);
                sleeper.sleep(waitMs);
            }

            state.setLastStartTimestampMs(clockSupplier.get());
            LOGGER.debug("RATE_LIMIT_ACQUIRED origin={} effectiveDelayMs={}", origin, effectiveDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            state.getSemaphore().release();
            throw new RuntimeException("Interrupted while waiting for rate limit permit for origin: " + origin, ex);
        } catch (Exception ex) {
            state.getSemaphore().release();
            throw ex;
        }
    }

    public void release(String originOrUrl) {
        String origin = canonicalizeOrigin(originOrUrl);
        OriginRateState state = stateCache.getIfPresent(origin);
        if (state != null) {
            state.getSemaphore().release();
            LOGGER.debug("RATE_LIMIT_RELEASED origin={}", origin);
        }
    }

    public long calculateEffectiveDelay(long robotsCrawlDelayMs) {
        long delayMs = robotsCrawlDelayMs >= 0 ? robotsCrawlDelayMs : defaultDelay.toMillis();
        return Math.min(Math.max(0, delayMs), maxDelay.toMillis());
    }

    public String canonicalizeOrigin(String originOrUrl) {
        try {
            URI uri = URI.create(originOrUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null || host == null) {
                return originOrUrl.toLowerCase();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(scheme.toLowerCase()).append("://").append(host.toLowerCase());

            if (port != -1 && !isStandardPort(scheme, port)) {
                sb.append(":").append(port);
            }

            return sb.toString();
        } catch (Exception ex) {
            return originOrUrl.toLowerCase();
        }
    }

    private boolean isStandardPort(String scheme, int port) {
        String lowerScheme = scheme.toLowerCase();
        return ("http".equals(lowerScheme) && port == 80) || ("https".equals(lowerScheme) && port == 443);
    }
}
