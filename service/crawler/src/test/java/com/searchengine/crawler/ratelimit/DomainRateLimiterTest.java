package com.searchengine.crawler.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DomainRateLimiterTest {

    private AtomicLong simulatedTime;
    private AtomicLong sleptDuration;
    private DomainRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        simulatedTime = new AtomicLong(1_000_000L);
        sleptDuration = new AtomicLong(0L);

        rateLimiter = new DomainRateLimiter(
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                1,
                1000,
                Duration.ofMinutes(30),
                simulatedTime::get,
                millis -> sleptDuration.addAndGet(millis)
        );
    }

    @Test
    void usesDefault2SecondDelayWhenRobotsDelayAbsent() {
        long effectiveDelay = rateLimiter.calculateEffectiveDelay(-1);
        assertThat(effectiveDelay).isEqualTo(2000L);
    }

    @Test
    void usesRobotsCrawlDelayWhenPresent() {
        long effectiveDelay = rateLimiter.calculateEffectiveDelay(5000L);
        assertThat(effectiveDelay).isEqualTo(5000L);
    }

    @Test
    void acceptsZeroCrawlDelay() {
        long effectiveDelay = rateLimiter.calculateEffectiveDelay(0L);
        assertThat(effectiveDelay).isEqualTo(0L);
    }

    @Test
    void fallsBackToDefaultOnNegativeCrawlDelay() {
        long effectiveDelay = rateLimiter.calculateEffectiveDelay(-500L);
        assertThat(effectiveDelay).isEqualTo(2000L);
    }

    @Test
    void capsCrawlDelayAt60Seconds() {
        long effectiveDelay = rateLimiter.calculateEffectiveDelay(120_000L);
        assertThat(effectiveDelay).isEqualTo(60_000L);
    }

    @Test
    void canonicalizesOriginsAndDefaultPorts() {
        String origin1 = rateLimiter.canonicalizeOrigin("https://example.com/path");
        String origin2 = rateLimiter.canonicalizeOrigin("https://EXAMPLE.COM:443/another");
        String origin3 = rateLimiter.canonicalizeOrigin("http://example.com:80/test");
        String origin4 = rateLimiter.canonicalizeOrigin("http://example.com:8080/test");

        assertThat(origin1).isEqualTo("https://example.com");
        assertThat(origin2).isEqualTo("https://example.com");
        assertThat(origin3).isEqualTo("http://example.com");
        assertThat(origin4).isEqualTo("http://example.com:8080");

        assertThat(origin1).isNotEqualTo(origin3);
    }

    @Test
    void calculatesWaitTimeForSecondRequestToSameOrigin() {
        String origin = "https://example.com";

        rateLimiter.acquire(origin, -1);
        assertThat(sleptDuration.get()).isEqualTo(0L);

        simulatedTime.addAndGet(500L); // 500ms elapsed since first start

        rateLimiter.release(origin);
        rateLimiter.acquire(origin, -1);

        assertThat(sleptDuration.get()).isEqualTo(1500L);
        rateLimiter.release(origin);
    }

    @Test
    void allowsDifferentOriginsToProceedIndependently() {
        String originA = "https://example.com";
        String originB = "https://spring.io";

        rateLimiter.acquire(originA, -1);
        rateLimiter.acquire(originB, -1);

        assertThat(sleptDuration.get()).isEqualTo(0L);

        rateLimiter.release(originA);
        rateLimiter.release(originB);
    }

    @Test
    void enforcesMaxConcurrencyOfOnePerOrigin() throws Exception {
        String origin = "https://example.com";

        rateLimiter.acquire(origin, -1);

        CountDownLatch threadStarted = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        Thread secondThread = new Thread(() -> {
            threadStarted.countDown();
            rateLimiter.acquire(origin, -1);
            secondAcquired.set(true);
            rateLimiter.release(origin);
        });

        secondThread.start();
        assertThat(threadStarted.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);

        assertThat(secondAcquired.get()).isFalse();

        rateLimiter.release(origin);
        secondThread.join(2000);

        assertThat(secondAcquired.get()).isTrue();
    }

    @Test
    void releasesPermitInFinallyOnException() {
        String origin = "https://example.com";

        rateLimiter.acquire(origin, -1);

        try {
            try {
                throw new RuntimeException("Fetch error");
            } finally {
                rateLimiter.release(origin);
            }
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).isEqualTo("Fetch error");
        }

        simulatedTime.addAndGet(3000L);
        rateLimiter.acquire(origin, -1);
        rateLimiter.release(origin);
    }
}
