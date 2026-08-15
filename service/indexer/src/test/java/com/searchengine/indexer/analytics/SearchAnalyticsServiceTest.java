package com.searchengine.indexer.analytics;

import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.analytics.SearchAnalyticsSnapshot;
import com.searchengine.indexer.model.analytics.ZeroResultsResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class SearchAnalyticsServiceTest {

    private SearchProperties searchProperties;
    private MeterRegistry meterRegistry;
    private SearchAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        searchProperties.getAnalytics().setEnabled(true);
        searchProperties.getAnalytics().setMaximumQueryEntries(100);
        searchProperties.getAnalytics().setTopQueryLimit(5);
        searchProperties.getAnalytics().setQueryRetention(Duration.ofHours(1));

        meterRegistry = new SimpleMeterRegistry();
        analyticsService = new SearchAnalyticsService(searchProperties, meterRegistry);
        analyticsService.init();
    }

    @Test
    void recordRequestAndSuccess_updatesCountersAndSnapshot() {
        analyticsService.recordRequest();
        analyticsService.recordSuccess("spring boot", 10L, 50L);

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(1L);
        assertThat(snapshot.successfulRequests()).isEqualTo(1L);
        assertThat(snapshot.resultfulSearches()).isEqualTo(1L);
        assertThat(snapshot.zeroResultSearches()).isEqualTo(0L);
        assertThat(snapshot.failedRequests()).isEqualTo(0L);
        assertThat(snapshot.validationErrors()).isEqualTo(0L);
        assertThat(snapshot.averageLatencyMs()).isEqualTo(50.0);
        assertThat(snapshot.maxLatencyMs()).isEqualTo(50L);
        assertThat(snapshot.topQueries()).hasSize(1);
        assertThat(snapshot.topQueries().get(0).query()).isEqualTo("spring boot");
        assertThat(snapshot.topQueries().get(0).count()).isEqualTo(1L);
    }

    @Test
    void recordZeroResultSearch_updatesZeroResultCounters() {
        analyticsService.recordRequest();
        analyticsService.recordSuccess("sprng boot", 0L, 20L);

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(1L);
        assertThat(snapshot.successfulRequests()).isEqualTo(1L);
        assertThat(snapshot.zeroResultSearches()).isEqualTo(1L);
        assertThat(snapshot.resultfulSearches()).isEqualTo(0L);

        ZeroResultsResponse zeroResults = analyticsService.getZeroResults();
        assertThat(zeroResults.queries()).hasSize(1);
        assertThat(zeroResults.queries().get(0).query()).isEqualTo("sprng boot");
        assertThat(zeroResults.queries().get(0).count()).isEqualTo(1L);
    }

    @Test
    void recordFailure_updatesFailureCounters() {
        analyticsService.recordRequest();
        analyticsService.recordFailure();

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(1L);
        assertThat(snapshot.failedRequests()).isEqualTo(1L);
        assertThat(snapshot.successfulRequests()).isEqualTo(0L);
    }

    @Test
    void recordValidationError_updatesValidationErrorCounters() {
        analyticsService.recordRequest();
        analyticsService.recordValidationError();

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(1L);
        assertThat(snapshot.validationErrors()).isEqualTo(1L);
    }

    @Test
    void popularQueries_sortedByCountThenZeroResultsThenQuery() {
        analyticsService.recordRequest();
        analyticsService.recordSuccess("java", 5L, 10L);

        analyticsService.recordRequest();
        analyticsService.recordSuccess("spring boot", 10L, 15L);
        analyticsService.recordRequest();
        analyticsService.recordSuccess("spring boot", 8L, 12L);

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.topQueries()).hasSize(2);
        assertThat(snapshot.topQueries().get(0).query()).isEqualTo("spring boot");
        assertThat(snapshot.topQueries().get(0).count()).isEqualTo(2L);
        assertThat(snapshot.topQueries().get(1).query()).isEqualTo("java");
        assertThat(snapshot.topQueries().get(1).count()).isEqualTo(1L);
    }

    @Test
    void memoryBoundEviction_doesNotReduceGlobalCounters() {
        searchProperties.getAnalytics().setMaximumQueryEntries(2);
        SearchAnalyticsService boundedService = new SearchAnalyticsService(searchProperties, meterRegistry);
        boundedService.init();

        boundedService.recordRequest();
        boundedService.recordSuccess("q1", 5L, 10L);
        boundedService.recordRequest();
        boundedService.recordSuccess("q2", 5L, 10L);
        boundedService.recordRequest();
        boundedService.recordSuccess("q3", 5L, 10L);

        SearchAnalyticsSnapshot snapshot = boundedService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(3L);
        assertThat(snapshot.successfulRequests()).isEqualTo(3L);
        assertThat(snapshot.trackedQueries()).isLessThanOrEqualTo(2L);
    }

    @Test
    void queryRetentionExpiration_removesOldQueryStats() throws InterruptedException {
        searchProperties.getAnalytics().setQueryRetention(Duration.ofMillis(50));
        SearchAnalyticsService expiringService = new SearchAnalyticsService(searchProperties, meterRegistry);
        expiringService.init();

        expiringService.recordRequest();
        expiringService.recordSuccess("expiring query", 1L, 10L);

        assertThat(expiringService.getSnapshot().topQueries()).hasSize(1);

        Thread.sleep(100);

        assertThat(expiringService.getSnapshot().topQueries()).isEmpty();
        // Global counters remain intact
        assertThat(expiringService.getSnapshot().totalRequests()).isEqualTo(1L);
    }

    @Test
    void disabledAnalytics_doesNotRecordMetrics() {
        searchProperties.getAnalytics().setEnabled(false);
        SearchAnalyticsService disabledService = new SearchAnalyticsService(searchProperties, meterRegistry);
        disabledService.init();

        disabledService.recordRequest();
        disabledService.recordSuccess("spring", 10L, 20L);

        SearchAnalyticsSnapshot snapshot = disabledService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(0L);
        assertThat(snapshot.successfulRequests()).isEqualTo(0L);
        assertThat(snapshot.topQueries()).isEmpty();
    }

    @Test
    void reset_clearsAnalyticsState() {
        analyticsService.recordRequest();
        analyticsService.recordSuccess("spring", 5L, 10L);
        assertThat(analyticsService.getSnapshot().totalRequests()).isEqualTo(1L);

        analyticsService.reset();

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(0L);
        assertThat(snapshot.successfulRequests()).isEqualTo(0L);
        assertThat(snapshot.topQueries()).isEmpty();
    }

    @Test
    void concurrentRequests_areThreadSafe() throws InterruptedException {
        int threads = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        analyticsService.recordRequest();
                        analyticsService.recordSuccess("concurrent query", 1L, 5L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        SearchAnalyticsSnapshot snapshot = analyticsService.getSnapshot();
        assertThat(snapshot.totalRequests()).isEqualTo(threads * requestsPerThread);
        assertThat(snapshot.successfulRequests()).isEqualTo(threads * requestsPerThread);
    }
}
